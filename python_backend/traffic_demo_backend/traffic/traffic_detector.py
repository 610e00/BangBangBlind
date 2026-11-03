
from pathlib import Path
import hashlib

import cv2
import torch
from ultralytics import YOLO
from .countdown_segmenter_v4 import (
    segment_countdown_digits, assert_not_frozen_holdout_path,
)
from .digit_recognizer_v3 import DigitRecognizerV3, EXPECTED_MODEL_SHA256


class TrafficDetector:
    """
    行人號誌辨識模組。

    功能：
    1. YOLO 偵測 pedestrian_red / pedestrian_green
    2. YOLO 偵測 countdown_timer
    3. Frozen segmenter v4 + recognizer v3 辨識倒數秒數
    4. 依 API 規格判斷是否安全通行
    """

    def __init__(self, yolo_model_path, digit_model_path=None):
        self.yolo_model_path = Path(yolo_model_path)

        if not self.yolo_model_path.exists():
            raise FileNotFoundError(
                f"找不到 YOLO 模型：{self.yolo_model_path}"
            )

        self.yolo_device = (
            0 if torch.cuda.is_available() else "cpu"
        )

        self.yolo_model = YOLO(
            str(self.yolo_model_path)
        )

        if digit_model_path is None:
            digit_model_path = (
                "/content/drive/MyDrive/AI_eye_project/checkpoints/9I2D_E3/"
                "final_v3_recognizer_9I2D_E3.pt"
            )
        digit_model_path = Path(digit_model_path)
        model_sha256 = hashlib.sha256(
            digit_model_path.read_bytes()
        ).hexdigest()
        if model_sha256 != EXPECTED_MODEL_SHA256:
            raise RuntimeError("Frozen digit model SHA256 mismatch")
        self.digit_recognizer = DigitRecognizerV3(model_path=digit_model_path)

    @staticmethod
    def _error_response(message):
        return {
            "status": "error",
            "message": message,
            "data": None
        }

    @staticmethod
    def _traffic_response(
        light_detected,
        light_color,
        seconds_remaining,
        is_safe_to_cross,
        message
    ):
        return {
            "status": "success",
            "message": message,
            "data": {
                "light_detected": light_detected,
                "light_color": light_color,
                "seconds_remaining": seconds_remaining,
                "is_safe_to_cross": is_safe_to_cross
            }
        }

    def detect(
        self,
        image_path,
        yolo_conf=0.25,
        signal_conf_threshold=0.50
    ):
        """
        辨識單張圖片中的行人號誌。

        API 安全規則：
        - red -> 不可通行
        - green 且剩餘 >= 15 秒 -> 可通行
        - green 且剩餘 < 15 秒 -> 不可通行
        """

        image_path = Path(image_path)
        try:
            assert_not_frozen_holdout_path(image_path.resolve())
        except RuntimeError as error:
            return self._error_response(str(error))

        if not image_path.exists():
            return self._error_response(
                "找不到圖片檔案"
            )

        image = cv2.imread(
            str(image_path)
        )

        if image is None:
            return self._error_response(
                "圖片格式無法讀取"
            )

        try:
            yolo_results = self.yolo_model.predict(
                source=str(image_path),
                imgsz=640,
                conf=yolo_conf,
                iou=0.5,
                device=self.yolo_device,
                verbose=False
            )

        except Exception as error:
            return self._error_response(
                f"號誌辨識失敗：{error}"
            )

        green_confidence = 0.0
        red_confidence = 0.0
        countdown_boxes = []

        for result in yolo_results:
            for box in result.boxes:
                class_id = int(
                    box.cls[0]
                )

                class_name = self.yolo_model.names[
                    class_id
                ]

                confidence = float(
                    box.conf[0]
                )

                coordinates = [
                    float(coordinate)
                    for coordinate
                    in box.xyxy[0].tolist()
                ]

                if class_name == "pedestrian_green":
                    green_confidence = max(
                        green_confidence,
                        confidence
                    )

                elif class_name == "pedestrian_red":
                    red_confidence = max(
                        red_confidence,
                        confidence
                    )

                elif class_name == "countdown_timer":
                    countdown_boxes.append({
                        "confidence": confidence,
                        "xyxy": coordinates
                    })

        # 紅燈優先
        if red_confidence >= signal_conf_threshold:
            return self._traffic_response(
                light_detected=True,
                light_color="red",
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="紅綠燈辨識完成"
            )

        # 沒有確認紅燈或綠燈
        if green_confidence < signal_conf_threshold:
            return self._traffic_response(
                light_detected=False,
                light_color=None,
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="未偵測到紅綠燈"
            )

        # 已偵測到綠燈，但沒有倒數計時器
        if not countdown_boxes:
            return self._traffic_response(
                light_detected=True,
                light_color="green",
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="紅綠燈辨識完成"
            )

        best_countdown_box = max(
            countdown_boxes,
            key=lambda countdown_box: countdown_box[
                "confidence"
            ]
        )

        x1, y1, x2, y2 = map(
            int,
            best_countdown_box["xyxy"]
        )

        image_height, image_width = image.shape[:2]

        box_width = max(
            x2 - x1,
            1
        )

        box_height = max(
            y2 - y1,
            1
        )

        pad_x = int(
            box_width * 0.10
        )

        pad_y = int(
            box_height * 0.10
        )

        x1 = max(
            0,
            x1 - pad_x
        )

        y1 = max(
            0,
            y1 - pad_y
        )

        x2 = min(
            image_width,
            x2 + pad_x
        )

        y2 = min(
            image_height,
            y2 + pad_y
        )

        countdown_crop = image[
            y1:y2,
            x1:x2
        ]

        if countdown_crop.size == 0:
            return self._traffic_response(
                light_detected=True,
                light_color="green",
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="紅綠燈辨識完成"
            )

        try:
            segmentation = segment_countdown_digits(countdown_crop)
            countdown_result = self.digit_recognizer.recognize_countdown_crops(
                segmentation["crops_bgr"]
            )
            seconds_remaining = countdown_result["seconds_remaining"]
            if type(seconds_remaining) is not int or not 0 <= seconds_remaining <= 99:
                raise ValueError("Invalid countdown seconds")
        except Exception:
            # Unreadable countdown must never authorize crossing.
            return self._traffic_response(
                light_detected=True,
                light_color="green",
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="紅綠燈辨識完成"
            )

        is_safe_to_cross = (
            seconds_remaining >= 15
        )

        return self._traffic_response(
            light_detected=True,
            light_color="green",
            seconds_remaining=seconds_remaining,
            is_safe_to_cross=is_safe_to_cross,
            message="紅綠燈辨識完成"
        )
