package br.com.fiap.ford.journey.application;

import br.com.fiap.ford.journey.domain.CustomerJourney;
import br.com.fiap.ford.journey.domain.DomainException;
import br.com.fiap.ford.journey.domain.JourneyId;
import br.com.fiap.ford.journey.domain.JourneyRepository;
import br.com.fiap.ford.journey.domain.JourneyStatus;
import br.com.fiap.ford.journey.domain.VehicleVin;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JourneyService {
    private final JourneyRepository repository;
    private final IntelligencePort intelligence;
    private final Clock clock;

    public JourneyService(JourneyRepository repository, IntelligencePort intelligence, Clock clock) {
        this.repository = repository;
        this.intelligence = intelligence;
        this.clock = clock;
    }

    @Transactional
    public JourneyView create(UUID customerId, String vin) {
        VehicleVin vehicleVin = new VehicleVin(vin);
        if (repository.existsActiveByVin(vehicleVin)) {
            throw new DomainException("vehicle already has an active journey");
        }
        return JourneyView.from(repository.save(CustomerJourney.start(customerId, vehicleVin, clock.instant())));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "journeys", key = "#id")
    public JourneyView find(UUID id) {
        return JourneyView.from(load(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "journeys", key = "#id")
    public JourneyView transition(UUID id, JourneyStatus target, String nextAction) {
        CustomerJourney journey = load(id);
        journey.transitionTo(target, nextAction, clock.instant());
        return JourneyView.from(repository.save(journey));
    }

    @Transactional
    @CacheEvict(cacheNames = "journeys", key = "#id")
    public RecommendationView recommend(UUID id, IntelligencePort.TelemetryCommand telemetry) {
        CustomerJourney journey = load(id);
        if (!journey.vin().value().equals(telemetry.vin().toUpperCase())) {
            throw new DomainException("telemetry VIN does not belong to this journey");
        }
        IntelligencePort.RecommendationResult result = intelligence.evaluate(telemetry);
        if (journey.status() == JourneyStatus.DETECTED) {
            journey.transitionTo(JourneyStatus.CONTACTED, "PRESENT_RECOMMENDATION", clock.instant());
        }
        if (journey.status() == JourneyStatus.CONTACTED) {
            journey.transitionTo(JourneyStatus.RECOMMENDED, result.action(), clock.instant());
        }
        repository.save(journey);
        return RecommendationView.from(result);
    }

    private CustomerJourney load(UUID id) {
        return repository.findById(new JourneyId(id)).orElseThrow(() -> new JourneyNotFoundException(id));
    }

    public record JourneyView(
            UUID id, UUID customerId, String vin, JourneyStatus status, String nextAction,
            Instant createdAt, Instant updatedAt) {
        static JourneyView from(CustomerJourney journey) {
            return new JourneyView(
                    journey.id().value(), journey.customerId(), journey.vin().value(), journey.status(),
                    journey.nextAction(), journey.createdAt(), journey.updatedAt());
        }
    }

    public record RecommendationView(
            String vin, String riskLevel, double riskScore, String action, java.util.List<String> reasons) {
        static RecommendationView from(IntelligencePort.RecommendationResult result) {
            return new RecommendationView(
                    result.vin(), result.riskLevel(), result.riskScore(), result.action(), result.reasons());
        }
    }
}

