"""Install once at app level; do not replace a team's existing handlers blindly."""
import logging
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from .schemas import build_error

LOGGER = logging.getLogger(__name__)


def install_error_handlers(app):
    @app.exception_handler(StarletteHTTPException)
    async def handle_http_error(request, exception):
        message = exception.detail if isinstance(exception.detail, str) else '請求無法處理'
        return JSONResponse(status_code=exception.status_code,
                            content=build_error(message).model_dump(by_alias=True),
                            headers=exception.headers)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(request, exception):
        return JSONResponse(status_code=422, content=build_error(
            '請提供正確的請求欄位，圖片欄位名稱為 file').model_dump(by_alias=True))

    @app.exception_handler(Exception)
    async def handle_unexpected_error(request, exception):
        LOGGER.error('Unhandled API error', exc_info=exception)
        return JSONResponse(status_code=500, content=build_error(
            '伺服器處理失敗').model_dump(by_alias=True))
