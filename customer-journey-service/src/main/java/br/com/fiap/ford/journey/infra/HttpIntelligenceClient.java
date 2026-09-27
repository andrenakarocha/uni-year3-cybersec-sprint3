package br.com.fiap.ford.journey.infra;

import br.com.fiap.ford.journey.application.IntelligencePort;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class HttpIntelligenceClient implements IntelligencePort {
    private final RestClient client;

    HttpIntelligenceClient(@Value("${clients.intelligence-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(requestFactory).baseUrl(baseUrl).build();
    }

    @Override
    @Retry(name = "vehicleIntelligence")
    @CircuitBreaker(name = "vehicleIntelligence", fallbackMethod = "fallback")
    public RecommendationResult evaluate(TelemetryCommand telemetry) {
        IntelligenceResponse response = client.post()
                .uri("/api/v1/recommendations/evaluate")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader())
                .body(TelemetryRequest.from(telemetry))
                .retrieve()
                .body(IntelligenceResponse.class);
        if (response == null) throw new IntelligenceUnavailableException(new IllegalStateException("empty response"));
        return response.toResult();
    }

    private RecommendationResult fallback(TelemetryCommand telemetry, Throwable error) {
        throw new IntelligenceUnavailableException(error);
    }

    private String authorizationHeader() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "";
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header == null ? "" : header;
    }

    private record TelemetryRequest(
            String vin,
            @JsonProperty("odometer_km") double odometerKm,
            @JsonProperty("oil_life_percent") double oilLifePercent,
            @JsonProperty("battery_voltage") double batteryVoltage,
            @JsonProperty("engine_temperature_c") double engineTemperatureC,
            @JsonProperty("diagnostic_codes") java.util.List<String> diagnosticCodes) {
        static TelemetryRequest from(TelemetryCommand command) {
            return new TelemetryRequest(
                    command.vin(), command.odometerKm(), command.oilLifePercent(), command.batteryVoltage(),
                    command.engineTemperatureC(), command.diagnosticCodes());
        }
    }

    private record IntelligenceResponse(
            String vin,
            @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("risk_score") double riskScore,
            String action,
            java.util.List<String> reasons) {
        RecommendationResult toResult() {
            return new RecommendationResult(vin, riskLevel, riskScore, action, reasons);
        }
    }
}
