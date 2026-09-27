package br.com.fiap.ford.journey.application;

import java.util.List;

public interface IntelligencePort {
    RecommendationResult evaluate(TelemetryCommand telemetry);

    record TelemetryCommand(
            String vin,
            double odometerKm,
            double oilLifePercent,
            double batteryVoltage,
            double engineTemperatureC,
            List<String> diagnosticCodes) {}

    record RecommendationResult(
            String vin, String riskLevel, double riskScore, String action, List<String> reasons) {}
}

