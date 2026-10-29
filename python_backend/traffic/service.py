"""Single-frame inference. No voice, fabricated seconds, or crossing direction inference."""
import hashlib
import logging
import os
import threading
from pathlib import Path

import numpy as np
import torch
from ultralytics import YOLO

from .countdown_segmenter_v4 import segment_countdown_digits
from .digit_recognizer_v3 import DigitRecognizerV3, EXPECTED_MODEL_SHA256
from .schemas import build_success

LOGGER = logging.getLogger(__name__)


class TrafficBusyError(RuntimeError):
    pass


def interpret_detections(detections, image_bgr, digit_recognizer, signal_conf_threshold=0.5):
    signals = [item for item in detections
               if item['class_name'] in ('pedestrian_red', 'pedestrian_green')
               and item['confidence'] >= signal_conf_threshold]
    # Multiple directions must not be merged into a crossing decision.
    if len(signals) != 1:
        return build_success()
    signal = signals[0]
    if signal['class_name'] == 'pedestrian_red':
        return build_success('red')
    left, top, right, bottom = signal['xyxy']
    signal_width, signal_height = right - left, bottom - top
    center_x, center_y = (left + right) / 2, (top + bottom) / 2
    countdown_candidates = []
    for item in detections:
        if item['class_name'] != 'countdown_timer' or item['confidence'] < 0.5:
            continue
        timer_left, timer_top, timer_right, timer_bottom = item['xyxy']
        timer_x = (timer_left + timer_right) / 2
        timer_y = (timer_top + timer_bottom) / 2
        if (abs(timer_x - center_x) <= max(signal_width, timer_right - timer_left)
                and abs(timer_y - center_y) <= 3 * max(signal_height, timer_bottom - timer_top)):
            countdown_candidates.append(item)
    if len(countdown_candidates) != 1:
        return build_success('green')
    left, top, right, bottom = countdown_candidates[0]['xyxy']
    pad_x, pad_y = (right - left) * 0.1, (bottom - top) * 0.1
    image_height, image_width = image_bgr.shape[:2]
    countdown_crop = image_bgr[max(0, int(top-pad_y)):min(image_height, int(bottom+pad_y)),
                               max(0, int(left-pad_x)):min(image_width, int(right+pad_x))]
    try:
        digit_crops = segment_countdown_digits(countdown_crop)['crops_bgr']
        recognition = digit_recognizer.recognize_countdown_crops(digit_crops)
        seconds_remaining = recognition['seconds_remaining']
        if (type(seconds_remaining) is not int or not 0 <= seconds_remaining <= 99
                or float(recognition['confidence']) < 0.8):
            return build_success('green')
        return build_success('green', seconds_remaining)
    except Exception:
        LOGGER.debug('Countdown segmentation or recognition failed', exc_info=True)
        return build_success('green')


class TrafficService:
    def __init__(self, model_directory=None, yolo_model_path=None, digit_model_path=None):
        model_directory = Path(model_directory or os.environ.get(
            'TRAFFIC_MODEL_DIR', Path(__file__).resolve().parent / 'models'))
        digit_checkpoint = Path(digit_model_path) if digit_model_path is not None else model_directory / 'final_v3_recognizer_9I2D_E3.pt'
        if hashlib.sha256(digit_checkpoint.read_bytes()).hexdigest() != EXPECTED_MODEL_SHA256:
            raise RuntimeError('CNN checkpoint hash mismatch')
        self.yolo_device = 0 if torch.cuda.is_available() else 'cpu'
        torch.set_num_threads(4)
        self.yolo_model = YOLO(str(yolo_model_path or model_directory / 'best_v5.pt'))
        expected_classes = {'countdown_timer', 'pedestrian_green', 'pedestrian_red'}
        if set(self.yolo_model.names.values()) != expected_classes:
            raise RuntimeError('Unexpected YOLO classes')
        self.digit_recognizer = DigitRecognizerV3(model_path=digit_checkpoint)
        self.inference_lock = threading.Lock()

    def detect(self, image_bgr, yolo_conf=0.25, signal_conf_threshold=0.5):
        if not self.inference_lock.acquire(blocking=False):
            raise TrafficBusyError('辨識忙碌中，請稍後再試')
        try:
            prediction = self.yolo_model.predict(
                source=image_bgr, imgsz=1280, conf=yolo_conf, iou=0.5,
                device=self.yolo_device, verbose=False,
            )[0]
            detections = [dict(
                class_name=self.yolo_model.names[int(box.cls.item())],
                confidence=float(box.conf.item()), xyxy=box.xyxy[0].tolist(),
            ) for box in prediction.boxes]
            return interpret_detections(detections, image_bgr, self.digit_recognizer, signal_conf_threshold)
        finally:
            self.inference_lock.release()


_service_instance = None
_initialization_lock = threading.Lock()


def get_traffic_service():
    global _service_instance
    with _initialization_lock:
        if _service_instance is None:
            _service_instance = TrafficService()
    return _service_instance
