package br.com.fiap.ford.journey.infra;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataJourneyRepository extends JpaRepository<JpaJourneyEntity, UUID> {
    @Query("select count(j) > 0 from JpaJourneyEntity j where j.vinHash = :vinHash and j.status not in ('COMPLETED', 'CANCELLED')")
    boolean existsActiveByVinHash(String vinHash);
}

