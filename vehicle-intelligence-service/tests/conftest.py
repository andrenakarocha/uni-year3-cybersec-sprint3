from datetime import UTC, datetime, timedelta

import jwt
import pytest
from fastapi.testclient import TestClient

from ford_intelligence.application.services import IntelligenceService
from ford_intelligence.config import get_settings
from ford_intelligence.infrastructure.repositories import InMemoryCache, InMemoryTelemetryRepository
from ford_intelligence.main import create_app
from ford_intelligence.pipeline.telemetry import TelemetryPipeline


@pytest.fixture
def service() -> IntelligenceService:
    return IntelligenceService(InMemoryTelemetryRepository(), InMemoryCache(), TelemetryPipeline())


@pytest.fixture
def client(service: IntelligenceService) -> TestClient:
    return TestClient(create_app(service), raise_server_exceptions=False)


@pytest.fixture
def token():
    def build(*roles: str, expired: bool = False, **claims) -> str:
        now = datetime.now(UTC)
        return jwt.encode(
            {
                "sub": "test-user",
                "iss": "ford-zero-touch",
                "aud": "ford-api",
                "iat": now,
                "exp": now + (timedelta(seconds=-1) if expired else timedelta(minutes=10)),
                "roles": list(roles),
                **claims,
            },
            get_settings().jwt_secret,
            algorithm="HS256",
        )

    return build


@pytest.fixture
def telemetry() -> dict:
    return {
        "vin": "1FMCU9GDXMUA12345",
        "odometer_km": 42420,
        "oil_life_percent": 12,
        "battery_voltage": 11.0,
        "engine_temperature_c": 120,
        "diagnostic_codes": ["P0217", "P0562"],
    }
