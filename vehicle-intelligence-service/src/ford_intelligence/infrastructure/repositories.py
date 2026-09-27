from typing import Any

from pymongo import DESCENDING, AsyncMongoClient
from redis.asyncio import Redis

from ford_intelligence.domain.models import Recommendation, TelemetryInput


class MongoTelemetryRepository:
    def __init__(self, mongo_url: str) -> None:
        self._collection = AsyncMongoClient(mongo_url).get_default_database()["telemetry"]

    async def save(self, telemetry: TelemetryInput, recommendation: Recommendation) -> None:
        await self._collection.insert_one(
            {
                "telemetry": telemetry.model_dump(mode="json"),
                "recommendation": recommendation.model_dump(mode="json"),
            }
        )

    async def latest_recommendation(self, vin: str) -> Recommendation | None:
        document: dict[str, Any] | None = await self._collection.find_one(
            {"telemetry.vin": vin}, sort=[("telemetry.captured_at", DESCENDING)]
        )
        return Recommendation.model_validate(document["recommendation"]) if document else None


class RedisRecommendationCache:
    def __init__(self, redis_url: str) -> None:
        self._redis = Redis.from_url(redis_url, decode_responses=True)

    async def get(self, key: str) -> Recommendation | None:
        value = await self._redis.get(key)
        return Recommendation.model_validate_json(value) if value else None

    async def set(self, key: str, value: Recommendation, ttl_seconds: int) -> None:
        await self._redis.set(key, value.model_dump_json(), ex=ttl_seconds)


class InMemoryTelemetryRepository:
    def __init__(self) -> None:
        self.items: dict[str, Recommendation] = {}

    async def save(self, telemetry: TelemetryInput, recommendation: Recommendation) -> None:
        self.items[telemetry.vin] = recommendation

    async def latest_recommendation(self, vin: str) -> Recommendation | None:
        return self.items.get(vin)


class InMemoryCache:
    def __init__(self) -> None:
        self.items: dict[str, Recommendation] = {}

    async def get(self, key: str) -> Recommendation | None:
        return self.items.get(key)

    async def set(self, key: str, value: Recommendation, ttl_seconds: int) -> None:
        self.items[key] = value
