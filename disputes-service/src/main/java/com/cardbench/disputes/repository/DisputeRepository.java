package com.cardbench.disputes.repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    @Override
    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    List<Dispute> findAll();

    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    List<Dispute> findAllByTransaction_Card_Cardmember_Id(String cardmemberId);

    @EntityGraph(attributePaths = {"transaction", "transaction.card", "transaction.card.cardmember"})
    Optional<Dispute> findDetailedById(Long id);

    boolean existsByTransaction_IdAndStatusIn(String transactionId, Collection<DisputeStatus> statuses);

    boolean existsByTransaction_IdAndStatus(String transactionId, DisputeStatus status);
}
