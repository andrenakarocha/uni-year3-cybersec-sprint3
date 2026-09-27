package br.com.fiap.ford.journey.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerJourneyTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final VehicleVin VIN = new VehicleVin("1FMCU9GDXMUA12345");

    @Test
    void startsDetectedAndTransitionsThroughHappyPath() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), VIN, NOW);

        assertThat(journey.id().value()).isNotNull();
        assertThat(journey.status()).isEqualTo(JourneyStatus.DETECTED);
        assertThat(journey.nextAction()).isEqualTo("CONTACT_CUSTOMER");
        assertThat(journey.createdAt()).isEqualTo(NOW);

        journey.transitionTo(JourneyStatus.CONTACTED, " present recommendation ", NOW.plusSeconds(1));
        journey.transitionTo(JourneyStatus.RECOMMENDED, "SCHEDULE", NOW.plusSeconds(2));
        journey.transitionTo(JourneyStatus.SCHEDULED, "ARRIVE", NOW.plusSeconds(3));
        journey.transitionTo(JourneyStatus.IN_SERVICE, "REPAIR", NOW.plusSeconds(4));
        journey.transitionTo(JourneyStatus.COMPLETED, "DONE", NOW.plusSeconds(5));

        assertThat(journey.status()).isEqualTo(JourneyStatus.COMPLETED);
        assertThat(journey.nextAction()).isEqualTo("DONE");
        assertThat(journey.updatedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void supportsCancellationOnlyFromActiveJourney() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), VIN, NOW);
        journey.transitionTo(JourneyStatus.CANCELLED, "CLOSED", NOW.plusSeconds(1));
        assertThat(journey.status()).isEqualTo(JourneyStatus.CANCELLED);
        assertThatThrownBy(() -> journey.transitionTo(JourneyStatus.CONTACTED, "NO", NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("invalid journey transition");
    }

    @Test
    void rejectsInvalidStateAndAction() {
        CustomerJourney journey = CustomerJourney.start(UUID.randomUUID(), VIN, NOW);
        assertThatThrownBy(() -> journey.transitionTo(JourneyStatus.SCHEDULED, "SKIP", NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> journey.transitionTo(JourneyStatus.CONTACTED, " ", NOW))
                .isInstanceOf(DomainException.class).hasMessage("next action is required");
    }

    @Test
    void restoresEveryProperty() {
        UUID customer = UUID.randomUUID();
        JourneyId id = JourneyId.newId();
        CustomerJourney restored = CustomerJourney.restore(
                id, customer, VIN, JourneyStatus.SCHEDULED, "ARRIVE", NOW, NOW.plusSeconds(1));
        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.customerId()).isEqualTo(customer);
        assertThat(restored.vin()).isEqualTo(VIN);
        assertThat(restored.status()).isEqualTo(JourneyStatus.SCHEDULED);
    }

    @Test
    void validatesIdentifiersAndVin() {
        assertThat(new VehicleVin("1fmcu9gdxmua12345").value()).isEqualTo(VIN.value());
        assertThatThrownBy(() -> new VehicleVin("invalid")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new VehicleVin(null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new JourneyId(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void publishesAllowedTargetsForEveryState() {
        assertThat(JourneyStatus.DETECTED.allowedTargets()).containsExactlyInAnyOrder(
                JourneyStatus.CONTACTED, JourneyStatus.CANCELLED);
        assertThat(JourneyStatus.CONTACTED.allowedTargets()).containsExactlyInAnyOrder(
                JourneyStatus.RECOMMENDED, JourneyStatus.CANCELLED);
        assertThat(JourneyStatus.RECOMMENDED.allowedTargets()).containsExactlyInAnyOrder(
                JourneyStatus.SCHEDULED, JourneyStatus.CANCELLED);
        assertThat(JourneyStatus.SCHEDULED.allowedTargets()).containsExactlyInAnyOrder(
                JourneyStatus.IN_SERVICE, JourneyStatus.CANCELLED);
        assertThat(JourneyStatus.IN_SERVICE.allowedTargets()).containsExactlyInAnyOrder(
                JourneyStatus.COMPLETED, JourneyStatus.CANCELLED);
        assertThat(JourneyStatus.COMPLETED.allowedTargets()).isEmpty();
        assertThat(JourneyStatus.CANCELLED.allowedTargets()).isEmpty();
    }
}

