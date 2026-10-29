"""Only this feature's router is included into the team app."""
import io
import logging
import warnings

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError
from PIL.JpegImagePlugin import JpegImageFile
from PIL.PngImagePlugin import PngImageFile

from .schemas import TrafficResponse
from .service import TrafficBusyError, get_traffic_service

LOGGER = logging.getLogger(__name__)
router = APIRouter(prefix='/traffic', tags=['traffic'])
MAX_UPLOAD_BYTES = 8 * 1024 * 1024


def decode_image(uploaded_bytes):
    if not uploaded_bytes:
        raise HTTPException(400, '照片是空的')
    if uploaded_bytes.startswith(b'\xff\xd8'):
        image_reader = JpegImageFile
    elif uploaded_bytes.startswith(b'\x89PNG\r\n\x1a\n'):
        image_reader = PngImageFile
    else:
        raise HTTPException(415, '僅接受 JPEG 或 PNG 圖片')
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('error', Image.DecompressionBombWarning)
            with image_reader(io.BytesIO(uploaded_bytes)) as source_image:
                if source_image.width * source_image.height > 20_000_000:
                    raise HTTPException(413, '照片尺寸過大')
                image_rgb = ImageOps.exif_transpose(source_image).convert('RGB')
                return cv2.cvtColor(np.asarray(image_rgb), cv2.COLOR_RGB2BGR)
    except (UnidentifiedImageError, OSError, ValueError,
            Image.DecompressionBombWarning, Image.DecompressionBombError, SyntaxError):
        raise HTTPException(415, '無法讀取照片，請上傳 JPEG 或 PNG 圖片') from None


@router.post('/detect', response_model=TrafficResponse, response_model_by_alias=True)
def detect_traffic(file: UploadFile = File(...)):
    try:
        uploaded_bytes = file.file.read(MAX_UPLOAD_BYTES + 1)
    finally:
        file.file.close()
    if len(uploaded_bytes) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, '照片請小於 8 MB')
    image_bgr = decode_image(uploaded_bytes)
    try:
        return get_traffic_service().detect(image_bgr)
    except TrafficBusyError as busy_error:
        raise HTTPException(429, str(busy_error)) from None
    except Exception:
        LOGGER.exception('Traffic inference failed')
        raise HTTPException(503, '號誌辨識服務暫時無法使用') from None
