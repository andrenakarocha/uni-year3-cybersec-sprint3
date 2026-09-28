from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Ford Vehicle Intelligence"
    # Obrigatório e sem default: sem JWT_SECRET o serviço não sobe. HS256 exige >= 256 bits.
    jwt_secret: str = Field(min_length=32)
    mongo_url: str = "mongodb://localhost:27017/telemetry"
    redis_url: str = "redis://localhost:6379/0"
    log_level: str = "INFO"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
