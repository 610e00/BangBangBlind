
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F


EXPECTED_MODEL_SHA256 = (
    "668b76522f85126632ee3e65da83743e4b80519edd1ff064b04adb9f496bbe2c"
)

EXPECTED_D5_SHA256 = (
    "c3d63a6b8f81143acea60d878f2bced258531b9e2a56cc14cb018933391c75c1"
)


# ----------------------------------------------------------------------------------------------------
# Holdout path firewall
# ----------------------------------------------------------------------------------------------------

FORBIDDEN_PATH_TOKENS = (
    "D11_new_holdout_source",
    "9I2D_D11B3",
    "9I2D_D11C",
    "D12_new_holdout_source",
    "9I2D_D12",
)


def assert_not_frozen_holdout_path(image_path):

    text = str(
        Path(image_path)
    )

    for token in FORBIDDEN_PATH_TOKENS:
        if token in text:
            raise RuntimeError(
                "HOLDOUT FIREWALL: development-time inference blocked. "
                f"Matched token={token!r}, path={text}"
            )


# ----------------------------------------------------------------------------------------------------
# Frozen architecture
# ----------------------------------------------------------------------------------------------------

class BaselineDigitCNN(nn.Module):

    def __init__(self):
        super().__init__()

        self.features = nn.Sequential(
            nn.Conv2d(
                3,
                32,
                kernel_size=3,
                padding=1,
            ),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),

            nn.Conv2d(
                32,
                64,
                kernel_size=3,
                padding=1,
            ),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),

            nn.Conv2d(
                64,
                128,
                kernel_size=3,
                padding=1,
            ),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2),

            nn.Conv2d(
                128,
                128,
                kernel_size=3,
                padding=1,
            ),
            nn.ReLU(inplace=True),

            nn.AdaptiveAvgPool2d(
                (4, 4)
            ),
        )

        self.classifier = nn.Sequential(
            nn.Flatten(),

            nn.Linear(
                128 * 4 * 4,
                128,
            ),

            nn.ReLU(inplace=True),

            nn.Dropout(0.3),

            nn.Linear(
                128,
                10,
            ),
        )

    def forward(self, x):
        x = self.features(x)
        x = self.classifier(x)
        return x


# ----------------------------------------------------------------------------------------------------
# Exact frozen D5 preprocessing
# ----------------------------------------------------------------------------------------------------

def preprocess_digit_crop_bgr(image_bgr):

    if image_bgr is None:
        raise ValueError(
            "image_bgr is None."
        )

    if not isinstance(
        image_bgr,
        np.ndarray,
    ):
        raise TypeError(
            "image_bgr must be numpy.ndarray."
        )

    if (
        image_bgr.ndim != 3
        or
        image_bgr.shape[2] != 3
    ):
        raise ValueError(
            "Expected HWC BGR image with 3 channels; "
            f"got shape={image_bgr.shape}"
        )

    if image_bgr.dtype != np.uint8:
        raise ValueError(
            "Expected uint8 BGR crop; "
            f"got dtype={image_bgr.dtype}"
        )

    h, w = image_bgr.shape[:2]

    if h <= 0 or w <= 0:
        raise ValueError(
            "Empty digit crop."
        )

    target_h = 64
    target_w = 64

    scale = min(
        target_w / w,
        target_h / h,
    )

    new_w = int(
        round(
            w * scale
        )
    )

    new_h = int(
        round(
            h * scale
        )
    )

    new_w = max(
        1,
        min(
            target_w,
            new_w,
        ),
    )

    new_h = max(
        1,
        min(
            target_h,
            new_h,
        ),
    )

    interpolation = (
        cv2.INTER_AREA
        if scale < 1.0
        else
        cv2.INTER_LINEAR
    )

    resized_bgr = cv2.resize(
        image_bgr,
        (
            new_w,
            new_h,
        ),
        interpolation=interpolation,
    )

    canvas_bgr = np.zeros(
        (
            target_h,
            target_w,
            3,
        ),
        dtype=np.uint8,
    )

    x0 = (
        target_w - new_w
    ) // 2

    y0 = (
        target_h - new_h
    ) // 2

    canvas_bgr[
        y0:y0 + new_h,
        x0:x0 + new_w,
    ] = resized_bgr

    image_rgb = cv2.cvtColor(
        canvas_bgr,
        cv2.COLOR_BGR2RGB,
    )

    image_float = (
        image_rgb.astype(
            np.float32
        )
        /
        255.0
    )

    chw = np.transpose(
        image_float,
        (
            2,
            0,
            1,
        ),
    )

    chw = np.ascontiguousarray(
        chw,
        dtype=np.float32,
    )

    return torch.from_numpy(
        chw
    )


# ----------------------------------------------------------------------------------------------------
# State dict loader
# ----------------------------------------------------------------------------------------------------

def extract_state_dict(obj):

    if not isinstance(
        obj,
        dict,
    ):
        raise RuntimeError(
            "Checkpoint object is not a dict."
        )

    keys = list(
        obj.keys()
    )

    direct_state = any(
        isinstance(key, str)
        and
        (
            key.startswith("features.")
            or
            key.startswith("classifier.")
            or
            key.startswith("module.features.")
            or
            key.startswith("module.classifier.")
        )
        for key in keys
    )

    if direct_state:
        return obj

    for key in (
        "state_dict",
        "model_state_dict",
        "best_model_state_dict",
        "model",
        "net",
    ):
        candidate = obj.get(key)

        if isinstance(
            candidate,
            dict,
        ):
            return candidate

    raise RuntimeError(
        "Could not locate recognizer state_dict."
    )


# ----------------------------------------------------------------------------------------------------
# Production recognizer
# ----------------------------------------------------------------------------------------------------

class DigitRecognizerV3:

    def __init__(
        self,
        model_path=None,
        device=None,
    ):

        if model_path is None:
            model_path = (
                "/content/drive/MyDrive/AI_eye_project/"
                "checkpoints/9I2D_E3/"
                "final_v3_recognizer_9I2D_E3.pt"
            )

        self.model_path = Path(
            model_path
        )

        if not self.model_path.exists():
            raise FileNotFoundError(
                self.model_path
            )

        if device is None:
            device = (
                "cuda"
                if torch.cuda.is_available()
                else
                "cpu"
            )

        self.device = torch.device(
            device
        )

        raw = torch.load(
            self.model_path,
            map_location="cpu",
            weights_only=False,
        )

        state = extract_state_dict(
            raw
        )

        normalized = {}

        for key, parameter_tensor in state.items():

            if key.startswith(
                "module."
            ):
                key = key[
                    len("module.") :
                ]

            normalized[
                key
            ] = parameter_tensor

        self.model = BaselineDigitCNN()

        self.model.load_state_dict(
            normalized,
            strict=True,
        )

        parameter_count = sum(
            p.numel()
            for p in self.model.parameters()
        )

        if parameter_count != 504394:
            raise RuntimeError(
                "Unexpected parameter count: "
                f"{parameter_count}"
            )

        self.model = self.model.to(
            self.device
        )

        self.model.eval()


    @torch.inference_mode()
    def recognize_digit_bgr(
        self,
        image_bgr,
    ):

        tensor = preprocess_digit_crop_bgr(
            image_bgr
        )

        batch = (
            tensor
            .unsqueeze(0)
            .to(self.device)
        )

        logits = self.model(
            batch
        )

        probabilities = F.softmax(
            logits,
            dim=1,
        )

        prediction = int(
            torch.argmax(
                logits,
                dim=1,
            ).item()
        )

        confidence = float(
            probabilities[
                0,
                prediction
            ].item()
        )

        return {
            "digit": prediction,
            "confidence": confidence,
        }


    def recognize_digit_path(
        self,
        image_path,
    ):

        assert_not_frozen_holdout_path(
            image_path
        )

        image_bgr = cv2.imread(
            str(image_path),
            cv2.IMREAD_COLOR,
        )

        if image_bgr is None:
            raise RuntimeError(
                f"Could not decode image: {image_path}"
            )

        return self.recognize_digit_bgr(
            image_bgr
        )


    def recognize_countdown_crops(
        self,
        digit_crops_bgr,
    ):

        if not isinstance(
            digit_crops_bgr,
            (list, tuple),
        ):
            raise TypeError(
                "digit_crops_bgr must be list or tuple."
            )

        if len(
            digit_crops_bgr
        ) not in (
            1,
            2,
        ):
            raise ValueError(
                "Expected exactly 1 or 2 digit crops."
            )

        digit_results = []

        for crop in digit_crops_bgr:
            result = self.recognize_digit_bgr(
                crop
            )

            digit_results.append(
                result
            )

        countdown_text = "".join(
            str(
                item["digit"]
            )
            for item in digit_results
        )

        seconds_remaining = int(
            countdown_text
        )

        confidence = min(
            float(
                item["confidence"]
            )
            for item in digit_results
        )

        return {
            "countdown_text": countdown_text,
            "seconds_remaining": seconds_remaining,
            "digit_count": len(digit_results),
            "digits": digit_results,
            "confidence": confidence,
        }


_DEFAULT_RECOGNIZER = None


def get_digit_recognizer(
    model_path=None,
    device=None,
):

    global _DEFAULT_RECOGNIZER

    if _DEFAULT_RECOGNIZER is None:
        _DEFAULT_RECOGNIZER = DigitRecognizerV3(
            model_path=model_path,
            device=device,
        )

    return _DEFAULT_RECOGNIZER
