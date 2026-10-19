package com.cardbench.disputes.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cardbench.disputes.domain.DisputeAdjustment;
import com.cardbench.disputes.domain.DisputeAdjustmentKind;

public interface DisputeAdjustmentRepository extends JpaRepository<DisputeAdjustment, Long> {
    Optional<DisputeAdjustment> findByDispute_IdAndKind(Long disputeId, DisputeAdjustmentKind kind);

    @EntityGraph(attributePaths = "dispute")
    List<DisputeAdjustment> findAllByDispute_Transaction_Card_IdOrderByAdjustmentDateAscIdAsc(String cardId);
}
