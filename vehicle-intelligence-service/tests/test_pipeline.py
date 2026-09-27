import pytest

from ford_intelligence.domain.models import RiskLevel, TelemetryFeatures, TelemetryInput
from ford_intelligence.pipeline.telemetry import (
    FeatureEngineeringFilter,
    PipelineContext,
    RiskScoringFilter,
    TelemetryPipeline,
)


@pytest.mark.parametrize(
    ("oil", "battery", "temperature", "codes", "expected"),
    [
        (100, 12.6, 90, [], RiskLevel.LOW),
        (25, 12.6, 100, [], RiskLevel.MEDIUM),
        (0, 10.0, 130, ["P1"], RiskLevel.HIGH),
        (0, 8.6, 150, ["P1", "P2", "P3", "P4"], RiskLevel.CRITICAL),
    ],
)
def test_pipeline_classifies_risk(oil, battery, temperature, codes, expected):
    item = TelemetryInput(
        vin="1fmcu9gdxmua12345",
        odometer_km=100,
        oil_life_percent=oil,
        battery_voltage=battery,
        engine_temperature_c=temperature,
        diagnostic_codes=codes,
    )
    result = TelemetryPipeline().execute(item)
    assert result.risk_level == expected
    assert result.vin == "1FMCU9GDXMUA12345"
    assert result.reasons


def test_risk_filter_requires_features():
    item = TelemetryInput(
        vin="1FMCU9GDXMUA12345",
        odometer_km=0,
        oil_life_percent=100,
        battery_voltage=12.6,
        engine_temperature_c=80,
    )
    with pytest.raises(ValueError, match="feature engineering"):
        RiskScoringFilter().apply(PipelineContext(item))


def test_pipeline_requires_recommendation():
    item = TelemetryInput(
        vin="1FMCU9GDXMUA12345",
        odometer_km=0,
        oil_life_percent=100,
        battery_voltage=12.6,
        engine_temperature_c=80,
    )
    with pytest.raises(ValueError, match="did not produce"):
        TelemetryPipeline([FeatureEngineeringFilter()]).execute(item)


def test_reasons_include_every_degraded_signal():
    context = PipelineContext(
        telemetry=TelemetryInput(
            vin="1FMCU9GDXMUA12345",
            odometer_km=1,
            oil_life_percent=1,
            battery_voltage=1,
            engine_temperature_c=150,
            diagnostic_codes=["P1"],
        ),
        features=TelemetryFeatures(1, 1, 1, 1),
    )
    result = RiskScoringFilter().apply(context).recommendation
    assert result is not None
    assert len(result.reasons) == 4
