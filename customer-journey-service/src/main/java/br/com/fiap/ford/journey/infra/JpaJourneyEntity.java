package br.com.fiap.ford.journey.infra;

import br.com.fiap.ford.journey.domain.CustomerJourney;
import br.com.fiap.ford.journey.domain.JourneyId;
import br.com.fiap.ford.journey.domain.JourneyStatus;
import br.com.fiap.ford.journey.domain.VehicleVin;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_journeys")
class JpaJourneyEntity {
    @Id UUID id;
    UUID customerId;
    String vin;
    @Enumerated(EnumType.STRING) JourneyStatus status;
    String nextAction;
    @Version long version;
    Instant createdAt;
    Instant updatedAt;

    protected JpaJourneyEntity() {}

    static JpaJourneyEntity from(CustomerJourney journey) {
        JpaJourneyEntity entity = new JpaJourneyEntity();
        entity.id = journey.id().value();
        entity.customerId = journey.customerId();
        entity.vin = journey.vin().value();
        return entity.updateFrom(journey);
    }

    JpaJourneyEntity updateFrom(CustomerJourney journey) {
        status = journey.status();
        nextAction = journey.nextAction();
        createdAt = journey.createdAt();
        updatedAt = journey.updatedAt();
        return this;
    }

    CustomerJourney toDomain() {
        return CustomerJourney.restore(
                new JourneyId(id), customerId, new VehicleVin(vin), status, nextAction, createdAt, updatedAt);
    }
}
