from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum

from pydantic import BaseModel, Field, field_validator


class RiskLevel(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class TelemetryInput(BaseModel):
    vin: str = Field(pattern=r"^[A-HJ-NPR-Z0-9]{17}$")
    odometer_km: float = Field(ge=0, le=2_000_000)
    oil_life_percent: float = Field(ge=0, le=100)
    battery_voltage: float = Field(ge=0, le=30)
    engine_temperature_c: float = Field(ge=-50, le=250)
    diagnostic_codes: list[str] = Field(default_factory=list, max_length=50)
    captured_at: datetime = Field(default_factory=lambda: datetime.now(UTC))

    @field_validator("vin", mode="before")
    @classmethod
    def normalize_vin(cls, value: object) -> object:
        return value.upper() if isinstance(value, str) else value


@dataclass(frozen=True, slots=True)
class TelemetryFeatures:
    oil_degradation: float
    battery_deviation: float
    thermal_stress: float
    diagnostic_severity: float


class Recommendation(BaseModel):
    vin: str
    risk_level: RiskLevel
    risk_score: float = Field(ge=0, le=1)
    action: str
    reasons: list[str]
    model_version: str = "rules-v1"
