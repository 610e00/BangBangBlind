"""Backward-compatible file interface; replaces PaddleOCR with the existing CNN."""
from pathlib import Path
from fastapi import HTTPException
from .routes import decode_image, MAX_UPLOAD_BYTES
from .schemas import build_error
from .service import TrafficService, TrafficBusyError


class TrafficDetector:
    def __init__(self, yolo_model_path=None, digit_model_path=None):
        self.service = TrafficService(yolo_model_path=yolo_model_path, digit_model_path=digit_model_path)
        self.yolo_model = self.service.yolo_model
        self.yolo_device = self.service.yolo_device
        self.digit_recognizer = self.service.digit_recognizer

    def detect(self, image_path, yolo_conf=0.25, signal_conf_threshold=0.50):
        """Preserves constructor/detect signatures used by the original module."""
        try:
            if not 0 <= yolo_conf <= 1 or not 0 <= signal_conf_threshold <= 1:
                return build_error('信心門檻必須介於0與1').model_dump(by_alias=True)
            source_path = Path(image_path)
            if source_path.stat().st_size > MAX_UPLOAD_BYTES:
                return build_error('照片請小於8MB').model_dump(by_alias=True)
            image_bgr = decode_image(source_path.read_bytes())
            return self.service.detect(image_bgr, yolo_conf=yolo_conf,
                                       signal_conf_threshold=signal_conf_threshold).model_dump(by_alias=True)
        except HTTPException as exception:
            return build_error(str(exception.detail)).model_dump(by_alias=True)
        except TrafficBusyError:
            return build_error('辨識忙碌中，請稍後再試').model_dump(by_alias=True)
        except (OSError, TypeError):
            return build_error('圖片路徑無法讀取').model_dump(by_alias=True)
        except Exception:
            return build_error('號誌辨識失敗').model_dump(by_alias=True)
