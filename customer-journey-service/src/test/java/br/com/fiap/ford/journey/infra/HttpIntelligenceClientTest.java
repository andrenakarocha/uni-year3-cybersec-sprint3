package br.com.fiap.ford.journey.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fiap.ford.journey.application.IntelligencePort.TelemetryCommand;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:resilience;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "resilience4j.circuitbreaker.instances.vehicleIntelligence.sliding-window-size=6",
        "resilience4j.circuitbreaker.instances.vehicleIntelligence.minimum-number-of-calls=6",
        "resilience4j.circuitbreaker.instances.vehicleIntelligence.wait-duration-in-open-state=500ms",
        "resilience4j.circuitbreaker.instances.vehicleIntelligence.permitted-number-of-calls-in-half-open-state=2",
        "resilience4j.retry.instances.vehicleIntelligence.wait-duration=1ms"
})
class HttpIntelligenceClientTest {
    private static final String VIN = "1FMCU9GDXMUA12345";
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicInteger STATUS = new AtomicInteger(200);
    private static volatile String authorization;
    private static volatile String body;
    private static final HttpServer SERVER = startServer();
    @Autowired HttpIntelligenceClient client;
    @Autowired CircuitBreakerRegistry registry;

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/recommendations/evaluate", exchange -> {
                CALLS.incrementAndGet();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = (STATUS.get() == 200
                        ? "{\"vin\":\"" + VIN + "\",\"risk_level\":\"HIGH\",\"risk_score\":0.8,"
                            + "\"action\":\"SCHEDULE_WITHIN_24_HOURS\",\"reasons\":[\"low oil life\"]}"
                        : "{\"detail\":\"dependency unavailable\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(STATUS.get(), response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry properties) {
        properties.add("clients.intelligence-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        registry.circuitBreaker("vehicleIntelligence").reset();
        CALLS.set(0);
        STATUS.set(200);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer forwarded-test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @AfterAll
    static void stopServer() { SERVER.stop(0); }

    @Test
    void forwardsBearerAndTranslatesTheRealHttpContract() {
        var result = client.evaluate(telemetry());
        assertThat(result.vin()).isEqualTo(VIN);
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.riskScore()).isEqualTo(.8);
        assertThat(result.action()).isEqualTo("SCHEDULE_WITHIN_24_HOURS");
        assertThat(result.reasons()).containsExactly("low oil life");
        assertThat(authorization).isEqualTo("Bearer forwarded-test-token");
        assertThat(body).contains("\"oil_life_percent\":10.0", "\"diagnostic_codes\":[\"P0217\"]");
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void boundsRetriesOpensCircuitAndRecoversAfterSuccessfulProbes() throws Exception {
        STATUS.set(503);
        assertThatThrownBy(() -> client.evaluate(telemetry())).isInstanceOf(IntelligenceUnavailableException.class);
        assertThat(CALLS.get()).isEqualTo(3);
        assertThatThrownBy(() -> client.evaluate(telemetry())).isInstanceOf(IntelligenceUnavailableException.class);
        assertThat(CALLS.get()).isEqualTo(6);
        CircuitBreaker breaker = registry.circuitBreaker("vehicleIntelligence");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.evaluate(telemetry())).isInstanceOf(IntelligenceUnavailableException.class);
        assertThat(CALLS.get()).isEqualTo(6);

        STATUS.set(200);
        Thread.sleep(550);
        assertThat(client.evaluate(telemetry()).riskLevel()).isEqualTo("HIGH");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(client.evaluate(telemetry()).riskLevel()).isEqualTo("HIGH");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(CALLS.get()).isEqualTo(8);
    }

    private TelemetryCommand telemetry() {
        return new TelemetryCommand(VIN, 100, 10, 12, 100, List.of("P0217"));
    }
}
