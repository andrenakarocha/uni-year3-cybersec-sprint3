from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

from ford_intelligence.domain.models import (
    Recommendation,
    RiskLevel,
    TelemetryFeatures,
    TelemetryInput,
)


@dataclass(slots=True)
class PipelineContext:
    telemetry: TelemetryInput
    features: TelemetryFeatures | None = None
    recommendation: Recommendation | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


class Filter(ABC):
    @abstractmethod
    def apply(self, context: PipelineContext) -> PipelineContext: ...


class FeatureEngineeringFilter(Filter):
    def apply(self, context: PipelineContext) -> PipelineContext:
        item = context.telemetry
        context.features = TelemetryFeatures(
            oil_degradation=(100 - item.oil_life_percent) / 100,
            battery_deviation=min(abs(12.6 - item.battery_voltage) / 4, 1),
            thermal_stress=min(max(item.engine_temperature_c - 95, 0) / 55, 1),
            diagnostic_severity=min(len(item.diagnostic_codes) / 4, 1),
        )
        return context


class RiskScoringFilter(Filter):
    _weights = (0.35, 0.20, 0.25, 0.20)

    def apply(self, context: PipelineContext) -> PipelineContext:
        if context.features is None:
            raise ValueError("feature engineering must run before risk scoring")
        features = context.features
        score = round(
            features.oil_degradation * self._weights[0]
            + features.battery_deviation * self._weights[1]
            + features.thermal_stress * self._weights[2]
            + features.diagnostic_severity * self._weights[3],
            4,
        )
        level, action = self._classify(score)
        reasons = self._reasons(features)
        context.recommendation = Recommendation(
            vin=context.telemetry.vin,
            risk_level=level,
            risk_score=score,
            action=action,
            reasons=reasons or ["vehicle signals are within expected ranges"],
        )
        return context

    @staticmethod
    def _classify(score: float) -> tuple[RiskLevel, str]:
        if score >= 0.75:
            return RiskLevel.CRITICAL, "STOP_AND_REQUEST_ROADSIDE_ASSISTANCE"
        if score >= 0.50:
            return RiskLevel.HIGH, "SCHEDULE_WITHIN_24_HOURS"
        if score >= 0.25:
            return RiskLevel.MEDIUM, "SCHEDULE_WITHIN_7_DAYS"
        return RiskLevel.LOW, "CONTINUE_MONITORING"

    @staticmethod
    def _reasons(features: TelemetryFeatures) -> list[str]:
        checks = (
            (features.oil_degradation >= 0.7, "low oil life"),
            (features.battery_deviation >= 0.3, "battery voltage outside nominal range"),
            (features.thermal_stress >= 0.2, "elevated engine temperature"),
            (features.diagnostic_severity > 0, "diagnostic trouble codes detected"),
        )
        return [message for applies, message in checks if applies]


class TelemetryPipeline:
    def __init__(self, filters: list[Filter] | None = None) -> None:
        self._filters = filters or [FeatureEngineeringFilter(), RiskScoringFilter()]

    def execute(self, telemetry: TelemetryInput) -> Recommendation:
        context = PipelineContext(telemetry=telemetry)
        for item in self._filters:
            context = item.apply(context)
        if context.recommendation is None:
            raise ValueError("pipeline did not produce a recommendation")
        return context.recommendation
