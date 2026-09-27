package br.com.fiap.ford.journey.infra;

import br.com.fiap.ford.journey.domain.CustomerJourney;
import br.com.fiap.ford.journey.domain.JourneyId;
import br.com.fiap.ford.journey.domain.JourneyRepository;
import br.com.fiap.ford.journey.domain.VehicleVin;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaJourneyRepositoryAdapter implements JourneyRepository {
    private final SpringDataJourneyRepository repository;
    private final FieldEncryption encryption;

    JpaJourneyRepositoryAdapter(SpringDataJourneyRepository repository, FieldEncryption encryption) {
        this.repository = repository;
        this.encryption = encryption;
    }

    public CustomerJourney save(CustomerJourney journey) {
        JpaJourneyEntity entity = repository.findById(journey.id().value())
                .map(existing -> existing.updateFrom(journey))
                .orElseGet(() -> JpaJourneyEntity.from(journey, encryption.blindIndex(journey.vin().value())));
        return repository.save(entity).toDomain();
    }

    public Optional<CustomerJourney> findById(JourneyId id) {
        return repository.findById(id.value()).map(JpaJourneyEntity::toDomain);
    }

    public boolean existsActiveByVin(VehicleVin vin) {
        return repository.existsActiveByVinHash(encryption.blindIndex(vin.value()));
    }
}
