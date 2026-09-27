from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Ford Vehicle Intelligence"
    jwt_secret: str = "zero-touch-development-secret-change-before-production-0123456789abcdef"
    mongo_url: str = "mongodb://localhost:27017/telemetry"
    redis_url: str = "redis://localhost:6379/0"
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
