import logging
import re
from http import HTTPStatus
from uuid import uuid4

import structlog
from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from ford_intelligence.api.problems import problem_response
from ford_intelligence.api.routes import router
from ford_intelligence.application.services import IntelligenceService
from ford_intelligence.config import get_settings
from ford_intelligence.infrastructure.repositories import (
    MongoTelemetryRepository,
    RedisRecommendationCache,
)
from ford_intelligence.pipeline.telemetry import TelemetryPipeline

# X-Request-ID vem do gateway; valor fora do formato é trocado para não permitir injeção de log.
SAFE_REQUEST_ID = re.compile(r"[A-Za-z0-9-]{8,64}")


def client_ip(request: Request) -> str:
    # X-Real-IP é sobrescrito pelo gateway; confiável porque o serviço não publica porta.
    fallback = request.client.host if request.client else "unknown"
    return request.headers.get("x-real-ip") or fallback


def create_app(service: IntelligenceService | None = None) -> FastAPI:
    settings = get_settings()
    logging.basicConfig(level=settings.log_level)
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.JSONRenderer(),
        ]
    )
    app = FastAPI(
        title=settings.app_name,
        version="1.0.0",
        description="Vehicle evaluation and maintenance recommendations by business rules.",
    )
    app.state.intelligence_service = service or IntelligenceService(
        MongoTelemetryRepository(settings.mongo_url),
        RedisRecommendationCache(settings.redis_url),
        TelemetryPipeline(),
    )
    app.include_router(router)

    @app.middleware("http")
    async def correlate_request(request: Request, call_next):
        incoming = request.headers.get("x-request-id", "")
        request_id = incoming if SAFE_REQUEST_ID.fullmatch(incoming) else str(uuid4())
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(request_id=request_id)
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response

    @app.get("/health", tags=["operations"])
    def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, error: HTTPException) -> JSONResponse:
        if error.status_code in (401, 403):
            denied = error.status_code == 403
            structlog.get_logger("security.audit").warning(
                "authz.denied" if denied else "auth.token.rejected",
                **{
                    "event.category": "authorization" if denied else "authentication",
                    "event.outcome": "denied" if denied else "failure",
                    "event.reason": str(error.detail),
                    "http.request.method": request.method,
                    "url.path": request.url.path,
                    "source.ip": client_ip(request),
                },
            )
        return problem_response(
            request, error.status_code, HTTPStatus(error.status_code).phrase,
            str(error.detail), error.headers,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
        errors = jsonable_encoder(error.errors())
        structlog.get_logger().warning(
            "request_validation_failed", path=request.url.path, validation_errors=errors
        )
        detail = "; ".join(
            f"{'.'.join(str(part) for part in item['loc'])}: {item['msg']}" for item in errors
        )
        return problem_response(request, 422, "Invalid request", detail)

    @app.exception_handler(Exception)
    async def unhandled_error(request: Request, error: Exception) -> JSONResponse:
        structlog.get_logger().exception(
            "unhandled_error", path=request.url.path, error_type=type(error).__name__
        )
        return problem_response(request, 500, "Unexpected error", "An unexpected error occurred.")

    return app


app = create_app()
