package br.com.fiap.ford.journey.domain;

import java.util.Optional;

public interface JourneyRepository {
    CustomerJourney save(CustomerJourney journey);
    Optional<CustomerJourney> findById(JourneyId id);
    boolean existsActiveByVin(VehicleVin vin);
}

