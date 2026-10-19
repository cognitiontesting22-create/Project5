package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Service
public class DisputeService {
    private final DisputeRepository disputes;
    private final CardTransactionRepository transactions;

    public DisputeService(DisputeRepository disputes, CardTransactionRepository transactions) {
        this.disputes = disputes;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<Dispute> listDisputes() {
        return disputes.findAll();
    }

    @Transactional(readOnly = true)
    public List<Dispute> listDisputesForCardmember(String cardmemberId) {
        return disputes.findAllByTransaction_Card_Cardmember_Id(cardmemberId);
    }

    @Transactional(readOnly = true)
    public Dispute getDispute(Long id) {
        return disputes.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + id));
    }

    @Transactional
    public Dispute fileDispute(String transactionId, BigDecimal amount, String reason) {
        CardTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        return disputes.save(new Dispute(transaction, amount, reason));
    }

    @Transactional
    public Dispute setStatus(Long id, DisputeStatus status, String resolutionNote) {
        Dispute dispute = getDispute(id);
        if (!dispute.getStatus().canTransitionTo(status)) {
            throw new InvalidDisputeTransitionException(
                    "Cannot transition dispute from " + dispute.getStatus() + " to " + status);
        }
        if (status.isResolvedOutcome()
                && (resolutionNote == null || resolutionNote.isBlank() || resolutionNote.length() > 500)) {
            throw new InvalidResolutionNoteException(
                    "A resolution note of 1 to 500 characters is required for a resolved outcome");
        }
        dispute.transitionTo(status, status.isResolvedOutcome() ? resolutionNote.trim() : null);
        return disputes.save(dispute);
    }

    @Transactional
    public Dispute withdrawDisputeForCardmember(Long id, String cardmemberId) {
        Dispute dispute = getDispute(id);
        if (!dispute.getTransaction().getCard().getCardmember().getId().equals(cardmemberId)) {
            throw new ResourceNotFoundException("Dispute not found: " + id);
        }
        return setStatus(id, DisputeStatus.WITHDRAWN, null);
    }
}
