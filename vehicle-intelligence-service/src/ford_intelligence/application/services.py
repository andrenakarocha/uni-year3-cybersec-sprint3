from typing import Protocol

import structlog

from ford_intelligence.domain.models import Recommendation, TelemetryInput
from ford_intelligence.pipeline.telemetry import TelemetryPipeline


class TelemetryRepository(Protocol):
    async def save(self, telemetry: TelemetryInput, recommendation: Recommendation) -> None: ...

    async def latest_recommendation(self, vin: str) -> Recommendation | None: ...


class Cache(Protocol):
    async def get(self, key: str) -> Recommendation | None: ...

    async def set(self, key: str, value: Recommendation, ttl_seconds: int) -> None: ...


class IntelligenceService:
    def __init__(
        self, repository: TelemetryRepository, cache: Cache, pipeline: TelemetryPipeline
    ) -> None:
        self._repository = repository
        self._cache = cache
        self._pipeline = pipeline

    async def evaluate(self, telemetry: TelemetryInput) -> Recommendation:
        recommendation = self._pipeline.execute(telemetry)
        # Base das métricas e alertas de ML: distribuição de risco por versão de modelo.
        # Sem VIN no log (dado pessoal); a correlação é pelo request_id.
        structlog.get_logger("ml.inference").info(
            "ml.inference",
            model_version=recommendation.model_version,
            risk_level=recommendation.risk_level,
            risk_score=recommendation.risk_score,
            action=recommendation.action,
            reasons=len(recommendation.reasons),
        )
        await self._repository.save(telemetry, recommendation)
        await self._cache.set(f"risk:{telemetry.vin}", recommendation, 300)
        return recommendation

    async def latest(self, vin: str) -> Recommendation | None:
        key = f"risk:{vin.upper()}"
        cached = await self._cache.get(key)
        if cached:
            return cached
        result = await self._repository.latest_recommendation(vin.upper())
        if result:
            await self._cache.set(key, result, 300)
        return result
