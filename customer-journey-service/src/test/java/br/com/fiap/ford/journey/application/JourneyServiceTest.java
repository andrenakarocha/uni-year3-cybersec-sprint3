package br.com.fiap.ford.journey.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.ford.journey.domain.CustomerJourney;
import br.com.fiap.ford.journey.domain.DomainException;
import br.com.fiap.ford.journey.domain.JourneyRepository;
import br.com.fiap.ford.journey.domain.JourneyStatus;
import br.com.fiap.ford.journey.domain.VehicleVin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JourneyServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final String VIN = "1FMCU9GDXMUA12345";
    @Mock JourneyRepository repository;
    @Mock IntelligencePort intelligence;
    JourneyService service;

    @BeforeEach
    void setUp() {
        service = new JourneyService(repository, intelligence, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsJourneyWhenVehicleHasNoActiveJourney() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(UUID.randomUUID(), VIN);
        assertThat(result.status()).isEqualTo(JourneyStatus.DETECTED);
        verify(repository).save(any());
    }

    @Test
    void rejectsDuplicateActiveJourney() {
        when(repository.existsActiveByVin(new VehicleVin(VIN))).thenReturn(true);
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), VIN))
                .isInstanceOf(DomainException.class).hasMessageContaining("active journey");
        verify(repository, never()).save(any());
    }

    @Test
    void findsTransitionsAndReportsMissingJourney() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), new VehicleVin(VIN), NOW);
        when(repository.findById(journey.id())).thenReturn(Optional.of(journey));
        when(repository.save(journey)).thenReturn(journey);
        assertThat(service.find(journey.id().value()).vin()).isEqualTo(VIN);
        assertThat(service.transition(journey.id().value(), JourneyStatus.CONTACTED, "NEXT").status())
                .isEqualTo(JourneyStatus.CONTACTED);

        UUID missing = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(missing)).isInstanceOf(JourneyNotFoundException.class);
    }

    @Test
    void recommendationAdvancesDetectedJourneyTwice() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), new VehicleVin(VIN), NOW);
        when(repository.findById(journey.id())).thenReturn(Optional.of(journey));
        when(intelligence.evaluate(any())).thenReturn(
                new IntelligencePort.RecommendationResult(VIN, "HIGH", .8, "SCHEDULE", List.of("oil")));
        var result = service.recommend(journey.id().value(), telemetry(VIN));
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(journey.status()).isEqualTo(JourneyStatus.RECOMMENDED);
        verify(repository).save(journey);
    }

    @Test
    void recommendationDoesNotRegressAdvancedJourney() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), new VehicleVin(VIN), NOW);
        journey.transitionTo(JourneyStatus.CONTACTED, "NEXT", NOW);
        journey.transitionTo(JourneyStatus.RECOMMENDED, "NEXT", NOW);
        when(repository.findById(journey.id())).thenReturn(Optional.of(journey));
        when(intelligence.evaluate(any())).thenReturn(
                new IntelligencePort.RecommendationResult(VIN, "LOW", .1, "MONITOR", List.of("ok")));
        service.recommend(journey.id().value(), telemetry(VIN));
        assertThat(journey.status()).isEqualTo(JourneyStatus.RECOMMENDED);
    }

    @Test
    void rejectsTelemetryFromAnotherVehicle() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), new VehicleVin(VIN), NOW);
        when(repository.findById(journey.id())).thenReturn(Optional.of(journey));
        assertThatThrownBy(() -> service.recommend(journey.id().value(), telemetry("1FMCU9GDXMUA99999")))
                .isInstanceOf(DomainException.class).hasMessageContaining("does not belong");
        verify(intelligence, never()).evaluate(any());
    }

    private IntelligencePort.TelemetryCommand telemetry(String vin) {
        return new IntelligencePort.TelemetryCommand(vin, 100, 10, 12, 100, List.of());
    }
}

