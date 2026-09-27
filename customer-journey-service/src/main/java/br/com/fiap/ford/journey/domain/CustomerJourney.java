package br.com.fiap.ford.journey.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CustomerJourney {
    private final JourneyId id;
    private final UUID customerId;
    private final VehicleVin vin;
    private JourneyStatus status;
    private String nextAction;
    private final Instant createdAt;
    private Instant updatedAt;

    private CustomerJourney(
            JourneyId id,
            UUID customerId,
            VehicleVin vin,
            JourneyStatus status,
            String nextAction,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.vin = Objects.requireNonNull(vin);
        this.status = Objects.requireNonNull(status);
        this.nextAction = requireAction(nextAction);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static CustomerJourney start(UUID customerId, VehicleVin vin, Instant now) {
        return new CustomerJourney(
                JourneyId.newId(), customerId, vin, JourneyStatus.DETECTED, "CONTACT_CUSTOMER", now, now);
    }

    public static CustomerJourney restore(
            JourneyId id, UUID customerId, VehicleVin vin, JourneyStatus status,
            String nextAction, Instant createdAt, Instant updatedAt) {
        return new CustomerJourney(id, customerId, vin, status, nextAction, createdAt, updatedAt);
    }

    public void transitionTo(JourneyStatus target, String action, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new DomainException("invalid journey transition from " + status + " to " + target);
        }
        status = target;
        nextAction = requireAction(action);
        updatedAt = Objects.requireNonNull(now);
    }

    private static String requireAction(String action) {
        if (action == null || action.isBlank()) throw new DomainException("next action is required");
        return action.trim();
    }

    public JourneyId id() { return id; }
    public UUID customerId() { return customerId; }
    public VehicleVin vin() { return vin; }
    public JourneyStatus status() { return status; }
    public String nextAction() { return nextAction; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

