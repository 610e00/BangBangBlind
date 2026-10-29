
from pathlib import Path

import cv2
import numpy as np


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
                "HOLDOUT FIREWALL: blocked path "
                f"{text}"
            )


def _validate_bgr(image):

    if image is None:
        raise ValueError("Image is None.")

    if not isinstance(image, np.ndarray):
        raise TypeError(
            "Image must be numpy.ndarray."
        )

    if (
        image.ndim != 3
        or
        image.shape[2] != 3
    ):
        raise ValueError(
            f"Invalid BGR image shape: {image.shape}"
        )

    if image.dtype != np.uint8:
        raise ValueError(
            f"Expected uint8; got {image.dtype}"
        )


# ====================================================================================================
# LED mask
# ====================================================================================================

def build_led_foreground_mask(
    countdown_roi_bgr,
):

    _validate_bgr(
        countdown_roi_bgr
    )

    hsv = cv2.cvtColor(
        countdown_roi_bgr,
        cv2.COLOR_BGR2HSV,
    )

    s = hsv[:, :, 1]
    v = hsv[:, :, 2]

    otsu, _ = cv2.threshold(
        v,
        0,
        255,
        cv2.THRESH_BINARY
        |
        cv2.THRESH_OTSU,
    )

    threshold = int(
        max(
            110,
            min(
                230,
                otsu,
            ),
        )
    )

    colored = (
        (v >= threshold)
        &
        (s >= 45)
    )

    white = (
        v
        >=
        max(
            195,
            threshold,
        )
    )

    mask = (
        colored
        |
        white
    ).astype(
        np.uint8
    ) * 255

    kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (2, 2),
    )

    mask = cv2.morphologyEx(
        mask,
        cv2.MORPH_OPEN,
        kernel,
        iterations=1,
    )

    return mask


# ====================================================================================================
# Basic helpers
# ====================================================================================================

def _foreground_bbox(mask):

    ys, xs = np.where(
        mask > 0
    )

    if len(xs) == 0:
        return None

    return (
        int(xs.min()),
        int(ys.min()),
        int(xs.max() + 1),
        int(ys.max() + 1),
    )


def _boolean_runs(active):

    runs = []

    start = None

    for i, is_active in enumerate(active):

        if is_active and start is None:
            start = i

        elif (
            not is_active
            and
            start is not None
        ):
            runs.append(
                (start, i)
            )
            start = None

    if start is not None:
        runs.append(
            (
                start,
                len(active),
            )
        )

    return runs


# ====================================================================================================
# Digit count
# Keep successful v2/v3 rule.
# ====================================================================================================

def _estimate_digit_count(
    mask,
    bbox,
):

    x1, y1, x2, y2 = bbox

    width = max(
        1,
        x2 - x1,
    )

    height = max(
        1,
        y2 - y1,
    )

    ratio = (
        width
        /
        height
    )

    return (
        1
        if ratio < 0.62
        else 2,
        float(ratio),
    )


# ====================================================================================================
# Build x-run candidates
# ====================================================================================================

def _build_x_run_candidates(
    mask,
):

    h, w = mask.shape

    projection = (
        mask > 0
    ).sum(
        axis=0
    )

    # Low threshold so thin digit "1" survives.
    column_threshold = max(
        1,
        int(
            round(
                h * 0.025
            )
        ),
    )

    active = (
        projection
        >=
        column_threshold
    )

    runs = _boolean_runs(
        active
    )

    candidates = []

    for x1, x2 in runs:

        if x2 <= x1:
            continue

        region = mask[
            :,
            x1:x2,
        ]

        ys, xs = np.where(
            region > 0
        )

        if len(xs) == 0:
            continue

        y1 = int(
            ys.min()
        )

        y2 = int(
            ys.max()
            +
            1
        )

        run_width = (
            x2
            -
            x1
        )

        run_height = (
            y2
            -
            y1
        )

        mass = int(
            np.count_nonzero(
                region
            )
        )

        center_x = (
            (
                x1
                +
                x2
            )
            /
            2.0
        )

        relative_center = (
            center_x
            /
            max(
                1,
                w
            )
        )

        width_fraction = (
            run_width
            /
            max(
                1,
                w
            )
        )

        height_fraction = (
            run_height
            /
            max(
                1,
                h
            )
        )

        candidates.append(
            {
                "x1":
                    int(x1),

                "x2":
                    int(x2),

                "y1":
                    int(y1),

                "y2":
                    int(y2),

                "width":
                    int(
                        run_width
                    ),

                "height":
                    int(
                        run_height
                    ),

                "mass":
                    int(
                        mass
                    ),

                "relative_center":
                    float(
                        relative_center
                    ),

                "width_fraction":
                    float(
                        width_fraction
                    ),

                "height_fraction":
                    float(
                        height_fraction
                    ),
            }
        )

    return (
        candidates,
        projection,
    )


# ====================================================================================================
# Reject frame / reflection candidates
# ====================================================================================================

def _is_edge_artifact(
    candidate,
):

    center = candidate[
        "relative_center"
    ]

    width_fraction = candidate[
        "width_fraction"
    ]

    height_fraction = candidate[
        "height_fraction"
    ]

    at_edge = (
        center < 0.14
        or
        center > 0.86
    )

    narrow = (
        width_fraction < 0.11
    )

    tall = (
        height_fraction > 0.55
    )

    return bool(
        at_edge
        and
        narrow
        and
        tall
    )


# ====================================================================================================
# Merge x-runs belonging to same digit if very close
# ====================================================================================================

def _merge_close_candidates(
    candidates,
    roi_width,
):

    if len(candidates) <= 1:
        return list(candidates)

    candidates = sorted(
        candidates,
        key=lambda c:
            c["x1"],
    )

    max_internal_gap = max(
        1,
        int(
            round(
                roi_width
                *
                0.018
            )
        ),
    )

    merged = []

    current = dict(
        candidates[0]
    )

    for candidate in candidates[1:]:

        gap = (
            candidate["x1"]
            -
            current["x2"]
        )

        if gap <= max_internal_gap:

            current["x2"] = max(
                current["x2"],
                candidate["x2"],
            )

            current["y1"] = min(
                current["y1"],
                candidate["y1"],
            )

            current["y2"] = max(
                current["y2"],
                candidate["y2"],
            )

            current["mass"] += (
                candidate["mass"]
            )

            current["width"] = (
                current["x2"]
                -
                current["x1"]
            )

            current["height"] = (
                current["y2"]
                -
                current["y1"]
            )

            current[
                "relative_center"
            ] = (
                (
                    current["x1"]
                    +
                    current["x2"]
                )
                /
                2.0
                /
                roi_width
            )

            current[
                "width_fraction"
            ] = (
                current["width"]
                /
                roi_width
            )

        else:

            merged.append(
                current
            )

            current = dict(
                candidate
            )

    merged.append(
        current
    )

    return merged


# ====================================================================================================
# Choose two digit candidates
# ====================================================================================================

def _choose_two_digit_candidates(
    candidates,
    roi_width,
):

    valid = [
        c
        for c in candidates
        if not _is_edge_artifact(
            c
        )
    ]

    valid = _merge_close_candidates(
        valid,
        roi_width,
    )

    if len(valid) < 2:
        return (
            None,
            valid,
        )

    # Consider all ordered pairs.
    pairs = []

    for i in range(
        len(valid)
    ):

        for j in range(
            i + 1,
            len(valid)
        ):

            left = valid[i]
            right = valid[j]

            separation = (
                right["x1"]
                -
                left["x2"]
            )

            center_distance = (
                right[
                    "relative_center"
                ]
                -
                left[
                    "relative_center"
                ]
            )

            # Avoid two fragments that are essentially the same digit.
            if center_distance < 0.12:
                continue

            total_mass = (
                left["mass"]
                +
                right["mass"]
            )

            # We prefer:
            # 1. strong foreground mass
            # 2. sensible left-right separation
            # 3. pair center not wildly displaced
            pair_center = (
                (
                    left[
                        "relative_center"
                    ]
                    +
                    right[
                        "relative_center"
                    ]
                )
                /
                2.0
            )

            center_penalty = abs(
                pair_center
                -
                0.5
            )

            score = (
                float(
                    total_mass
                )
                -
                (
                    center_penalty
                    *
                    total_mass
                    *
                    0.20
                )
            )

            pairs.append(
                (
                    score,
                    left,
                    right,
                    separation,
                )
            )

    if not pairs:
        return (
            None,
            valid,
        )

    pairs.sort(
        key=lambda item:
            item[0],
        reverse=True,
    )

    _, left, right, _ = (
        pairs[0]
    )

    return (
        [
            left,
            right,
        ],
        valid,
    )


# ====================================================================================================
# Candidate -> padded box
# ====================================================================================================

def _candidate_to_box(
    candidate,
    full_w,
    full_h,
):

    x_pad = max(
        2,
        int(
            round(
                full_w
                *
                0.018
            )
        ),
    )

    y_pad = max(
        2,
        int(
            round(
                full_h
                *
                0.030
            )
        ),
    )

    return [
        max(
            0,
            candidate["x1"]
            -
            x_pad,
        ),

        max(
            0,
            candidate["y1"]
            -
            y_pad,
        ),

        min(
            full_w,
            candidate["x2"]
            +
            x_pad,
        ),

        min(
            full_h,
            candidate["y2"]
            +
            y_pad,
        ),
    ]


# ====================================================================================================
# Main
# ====================================================================================================

def segment_countdown_digits(
    countdown_roi_bgr,
    return_debug=False,
):

    _validate_bgr(
        countdown_roi_bgr
    )

    roi = countdown_roi_bgr

    h, w = roi.shape[
        :2
    ]

    mask = build_led_foreground_mask(
        roi
    )

    active_box = _foreground_bbox(
        mask
    )

    if active_box is None:
        raise RuntimeError(
            "No LED foreground."
        )

    digit_count, aspect_ratio = (
        _estimate_digit_count(
            mask,
            active_box,
        )
    )

    candidates, projection = (
        _build_x_run_candidates(
            mask
        )
    )

    selected_candidates = None
    strategy = None

    # -----------------------------------------------------------------------------------------------
    # 1 digit
    # -----------------------------------------------------------------------------------------------

    if digit_count == 1:

        ax1, ay1, ax2, ay2 = (
            active_box
        )

        x_pad = max(
            2,
            int(
                round(
                    w * 0.018
                )
            ),
        )

        y_pad = max(
            2,
            int(
                round(
                    h * 0.030
                )
            ),
        )

        boxes = [
            [
                max(
                    0,
                    ax1 - x_pad,
                ),
                max(
                    0,
                    ay1 - y_pad,
                ),
                min(
                    w,
                    ax2 + x_pad,
                ),
                min(
                    h,
                    ay2 + y_pad,
                ),
            ]
        ]

        strategy = (
            "single_AR"
        )

    # -----------------------------------------------------------------------------------------------
    # 2 digits
    # -----------------------------------------------------------------------------------------------

    else:

        (
            selected_candidates,
            valid_candidates,
        ) = _choose_two_digit_candidates(
            candidates,
            w,
        )

        if (
            selected_candidates
            is not None
        ):

            boxes = [
                _candidate_to_box(
                    candidate,
                    w,
                    h,
                )
                for candidate in (
                    selected_candidates
                )
            ]

            strategy = (
                "direct_x_run_pair"
            )

        else:

            # ----------------------------------------------------------------------------------------
            # Conservative fallback:
            # midpoint split of foreground bbox.
            # This should be rare and is retained only so module fails gracefully.
            # ----------------------------------------------------------------------------------------

            ax1, ay1, ax2, ay2 = (
                active_box
            )

            split_x = int(
                round(
                    (
                        ax1
                        +
                        ax2
                    )
                    /
                    2.0
                )
            )

            left_mask = mask[
                ay1:ay2,
                ax1:split_x,
            ]

            right_mask = mask[
                ay1:ay2,
                split_x:ax2,
            ]

            left_box_local = (
                _foreground_bbox(
                    left_mask
                )
            )

            right_box_local = (
                _foreground_bbox(
                    right_mask
                )
            )

            if (
                left_box_local
                is None
                or
                right_box_local
                is None
            ):

                raise RuntimeError(
                    "Unable to resolve two digit candidates."
                )

            boxes = []

            for (
                local_box,
                offset_x,
            ) in (
                (
                    left_box_local,
                    ax1,
                ),
                (
                    right_box_local,
                    split_x,
                ),
            ):

                lx1, ly1, lx2, ly2 = (
                    local_box
                )

                candidate = {
                    "x1":
                        offset_x
                        +
                        lx1,

                    "x2":
                        offset_x
                        +
                        lx2,

                    "y1":
                        ay1
                        +
                        ly1,

                    "y2":
                        ay1
                        +
                        ly2,
                }

                boxes.append(
                    _candidate_to_box(
                        candidate,
                        w,
                        h,
                    )
                )

            strategy = (
                "midpoint_fallback"
            )

            valid_candidates = []


    boxes = sorted(
        boxes,
        key=lambda box:
            box[0],
    )

    crops = []

    for box in boxes:

        x1, y1, x2, y2 = box

        crop = roi[
            y1:y2,
            x1:x2,
        ].copy()

        if crop.size == 0:
            raise RuntimeError(
                "Empty crop."
            )

        crops.append(
            crop
        )

    result = {
        "digit_count":
            int(
                digit_count
            ),

        "boxes_xyxy":
            boxes,

        "crops_bgr":
            crops,

        "foreground_aspect_ratio":
            float(
                aspect_ratio
            ),

        "strategy":
            strategy,
    }

    if return_debug:

        result["debug"] = {
            "mask":
                mask,

            "projection":
                projection.copy(),

            "raw_candidates":
                candidates,

            "valid_candidates":
                (
                    valid_candidates
                    if digit_count == 2
                    else candidates
                ),

            "selected_candidates":
                selected_candidates,

            "strategy":
                strategy,
        }

    return result
