package br.com.fiap.ford.journey.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JourneyRepository {
    CustomerJourney save(CustomerJourney journey);
    Optional<CustomerJourney> findById(JourneyId id);
    boolean existsActiveByVin(VehicleVin vin);
    List<VehicleVin> vinsOwnedBy(UUID customerId);
}

