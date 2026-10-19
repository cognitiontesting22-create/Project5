package com.cardbench.disputes.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.cardbench.disputes.domain.Dispute;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    @Override
    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    List<Dispute> findAll();

    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    List<Dispute> findAllByTransaction_Card_Cardmember_Id(String cardmemberId);

    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    Optional<Dispute> findDetailedById(Long id);
}
