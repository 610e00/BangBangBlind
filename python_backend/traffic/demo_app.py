"""Standalone host; team integrators include the router in their existing main.py."""
from contextlib import asynccontextmanager
from fastapi import FastAPI
from starlette.concurrency import run_in_threadpool
from .routes import router as traffic_router
from .error_handlers import install_error_handlers
from .service import get_traffic_service


@asynccontextmanager
async def lifespan(app):
    # Load before accepting frames so the first user request need not load weights.
    await run_in_threadpool(get_traffic_service)
    yield


app = FastAPI(title='BangBangBlind 行人號誌模組', version='1.0.0', lifespan=lifespan)
install_error_handlers(app)
app.include_router(traffic_router)
