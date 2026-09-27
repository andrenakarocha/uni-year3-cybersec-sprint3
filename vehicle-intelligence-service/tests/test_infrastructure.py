from ford_intelligence.config import Settings, get_settings
from ford_intelligence.domain.models import Recommendation, RiskLevel, TelemetryInput
from ford_intelligence.infrastructure import repositories


def recommendation() -> Recommendation:
    return Recommendation(
        vin="1FMCU9GDXMUA12345",
        risk_level=RiskLevel.LOW,
        risk_score=0.1,
        action="CONTINUE_MONITORING",
        reasons=["ok"],
    )


class FakeCollection:
    def __init__(self):
        self.document = None

    async def insert_one(self, document):
        self.document = document

    async def find_one(self, *_args, **_kwargs):
        return self.document


class FakeDatabase:
    def __init__(self, collection):
        self.collection = collection

    def __getitem__(self, _):
        return self.collection


class FakeMongoClient:
    collection = FakeCollection()

    def __init__(self, _):
        pass

    def get_default_database(self):
        return FakeDatabase(self.collection)


class FakeRedis:
    def __init__(self):
        self.value = None

    async def get(self, _):
        return self.value

    async def set(self, _key, value, ex):
        assert ex == 300
        self.value = value


async def test_mongo_repository_round_trip(monkeypatch):
    monkeypatch.setattr(repositories, "AsyncMongoClient", FakeMongoClient)
    repository = repositories.MongoTelemetryRepository("mongodb://example/db")
    telemetry = TelemetryInput(
        vin=recommendation().vin,
        odometer_km=1,
        oil_life_percent=100,
        battery_voltage=12.6,
        engine_temperature_c=90,
    )
    await repository.save(telemetry, recommendation())
    assert await repository.latest_recommendation(telemetry.vin) == recommendation()
    FakeMongoClient.collection.document = None
    assert await repository.latest_recommendation(telemetry.vin) is None


async def test_redis_cache_round_trip():
    cache = repositories.RedisRecommendationCache.__new__(repositories.RedisRecommendationCache)
    cache._redis = FakeRedis()
    assert await cache.get("missing") is None
    await cache.set("risk:vin", recommendation(), 300)
    assert await cache.get("risk:vin") == recommendation()


def test_settings_support_environment(monkeypatch):
    monkeypatch.setenv("LOG_LEVEL", "DEBUG")
    get_settings.cache_clear()
    assert get_settings().log_level == "DEBUG"
    assert Settings().app_name == "Ford Vehicle Intelligence"
    get_settings.cache_clear()
