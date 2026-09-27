package br.com.fiap.ford.journey.domain;

import java.util.EnumSet;
import java.util.Set;

public enum JourneyStatus {
    DETECTED, CONTACTED, RECOMMENDED, SCHEDULED, IN_SERVICE, COMPLETED, CANCELLED;

    public boolean canTransitionTo(JourneyStatus target) {
        if (target == CANCELLED) return this != COMPLETED && this != CANCELLED;
        return switch (this) {
            case DETECTED -> target == CONTACTED;
            case CONTACTED -> target == RECOMMENDED;
            case RECOMMENDED -> target == SCHEDULED;
            case SCHEDULED -> target == IN_SERVICE;
            case IN_SERVICE -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public Set<JourneyStatus> allowedTargets() {
        return EnumSet.allOf(JourneyStatus.class).stream()
                .filter(this::canTransitionTo)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

