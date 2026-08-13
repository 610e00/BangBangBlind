
from pathlib import Path
import re
import tempfile

import cv2
import torch
from ultralytics import YOLO
from paddleocr import PaddleOCR


class TrafficDetector:
    """
    行人號誌辨識模組。

    功能：
    1. YOLO 偵測 pedestrian_red / pedestrian_green
    2. YOLO 偵測 countdown_timer
    3. PaddleOCR 辨識倒數秒數
    4. 依 API 規格判斷是否安全通行
    """

    def __init__(self, yolo_model_path):
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

        self.ocr = PaddleOCR(
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            enable_mkldnn=False,
            device="cpu"
        )

    @staticmethod
    def _normalize_ocr_item(item):
        """
        將不同 PaddleOCR 版本的結果整理成 dict。
        """

        if isinstance(item, dict):
            ocr_payload = item

        elif hasattr(item, "json"):
            ocr_payload = item.json

            if callable(ocr_payload):
                ocr_payload = ocr_payload()

        else:
            try:
                ocr_payload = dict(item)
            except Exception:
                return {}

        if not isinstance(ocr_payload, dict):
            return {}

        if (
            "res" in ocr_payload
            and isinstance(ocr_payload["res"], dict)
        ):
            ocr_payload = ocr_payload["res"]

        return ocr_payload

    @classmethod
    def _extract_ocr_seconds(
        cls,
        ocr_result,
        min_score=0.10
    ):
        """
        從 PaddleOCR 結果取得倒數秒數。
        """

        candidates = []

        for ocr_item in ocr_result:
            ocr_payload = cls._normalize_ocr_item(
                ocr_item
            )

            recognized_texts = ocr_payload.get(
                "rec_texts",
                []
            )

            recognition_scores = ocr_payload.get(
                "rec_scores",
                []
            )

            for recognized_text, recognition_score in zip(
                recognized_texts,
                recognition_scores
            ):
                raw_text = str(
                    recognized_text
                ).strip()

                recognition_score = float(
                    recognition_score
                )

                digits_text = re.sub(
                    r"\D",
                    "",
                    raw_text
                )

                if not digits_text:
                    continue

                seconds_number = int(
                    digits_text
                )

                if (
                    0 <= seconds_number <= 99
                    and recognition_score >= min_score
                ):
                    candidates.append({
                        "seconds": seconds_number,
                        "confidence": recognition_score,
                        "raw_text": raw_text
                    })

        if not candidates:
            return None

        return max(
            candidates,
            key=lambda candidate: candidate["confidence"]
        )

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
        signal_conf_threshold=0.50,
        ocr_conf_threshold=0.80
    ):
        """
        辨識單張圖片中的行人號誌。

        API 安全規則：
        - red -> 不可通行
        - green 且剩餘 >= 15 秒 -> 可通行
        - green 且剩餘 < 15 秒 -> 不可通行
        """

        image_path = Path(image_path)

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

        temporary_file_path = None

        try:
            with tempfile.NamedTemporaryFile(
                suffix=".png",
                delete=False
            ) as temporary_file:
                temporary_file_path = Path(
                    temporary_file.name
                )

            crop_saved = cv2.imwrite(
                str(temporary_file_path),
                countdown_crop
            )

            if not crop_saved:
                return self._error_response(
                    "倒數圖片處理失敗"
                )

            ocr_result = self.ocr.predict(
                str(temporary_file_path),
                text_rec_score_thresh=0.10
            )

        except Exception as error:
            return self._error_response(
                f"倒數秒數辨識失敗：{error}"
            )

        finally:
            if (
                temporary_file_path is not None
                and temporary_file_path.exists()
            ):
                temporary_file_path.unlink()

        ocr_result_payload = self._extract_ocr_seconds(
            ocr_result,
            min_score=0.10
        )

        if ocr_result_payload is None:
            return self._traffic_response(
                light_detected=True,
                light_color="green",
                seconds_remaining=None,
                is_safe_to_cross=False,
                message="紅綠燈辨識完成"
            )

        seconds_remaining = int(
            ocr_result_payload["seconds"]
        )

        ocr_confidence = float(
            ocr_result_payload["confidence"]
        )

        if ocr_confidence < ocr_conf_threshold:
            is_safe_to_cross = False
        else:
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
