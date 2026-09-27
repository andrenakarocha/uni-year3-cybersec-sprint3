from ford_intelligence.application.services import IntelligenceService
from ford_intelligence.domain.models import TelemetryInput
from ford_intelligence.infrastructure.repositories import InMemoryCache, InMemoryTelemetryRepository
from ford_intelligence.pipeline.telemetry import TelemetryPipeline


def item() -> TelemetryInput:
    return TelemetryInput(
        vin="1FMCU9GDXMUA12345",
        odometer_km=10,
        oil_life_percent=50,
        battery_voltage=12.6,
        engine_temperature_c=90,
    )


async def test_evaluate_persists_and_populates_cache():
    repository, cache = InMemoryTelemetryRepository(), InMemoryCache()
    service = IntelligenceService(repository, cache, TelemetryPipeline())
    result = await service.evaluate(item())
    assert await repository.latest_recommendation(item().vin) == result
    assert await cache.get(f"risk:{item().vin}") == result


async def test_latest_uses_cache_before_repository():
    repository, cache = InMemoryTelemetryRepository(), InMemoryCache()
    service = IntelligenceService(repository, cache, TelemetryPipeline())
    result = await service.evaluate(item())
    repository.items.clear()
    assert await service.latest(item().vin.lower()) == result


async def test_latest_backfills_cache_and_handles_missing():
    repository, cache = InMemoryTelemetryRepository(), InMemoryCache()
    service = IntelligenceService(repository, cache, TelemetryPipeline())
    result = TelemetryPipeline().execute(item())
    repository.items[item().vin] = result
    assert await service.latest(item().vin) == result
    assert await cache.get(f"risk:{item().vin}") == result
    assert await service.latest("NOTFOUND000000000") is None
