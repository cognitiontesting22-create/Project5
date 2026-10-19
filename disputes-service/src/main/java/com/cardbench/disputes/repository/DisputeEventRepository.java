package com.cardbench.disputes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;

public interface DisputeEventRepository extends JpaRepository<DisputeEvent, Long> {
    List<DisputeEvent> findAllByOrderByOccurredAtAscIdAsc();

    List<DisputeEvent> findAllByDispute_IdOrderByOccurredAtAscIdAsc(Long disputeId);

    List<DisputeEvent> findAllByDispute_Transaction_Card_Cardmember_IdAndInternalFalseOrderByOccurredAtAscIdAsc(
            String cardmemberId);

    boolean existsByDispute_IdAndKind(Long disputeId, DisputeEventKind kind);
}
