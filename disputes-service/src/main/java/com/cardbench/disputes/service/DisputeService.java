package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Service
public class DisputeService {
    private final DisputeRepository disputes;
    private final CardTransactionRepository transactions;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes, CardTransactionRepository transactions, Clock clock) {
        this.disputes = disputes;
        this.transactions = transactions;
        this.clock = clock;
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
    public Dispute fileDispute(String transactionId, BigDecimal amount, String reasonCode, String description) {
        CardTransaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        FilingBlock filingBlock = filingBlock(transaction);
        if (filingBlock != null) {
            throw filingError(filingBlock.code(), filingBlock.message(), filingBlock.status());
        }
        if (amount == null || amount.signum() <= 0) {
            throw filingError("AMOUNT_MUST_BE_POSITIVE", "The disputed amount must be more than zero.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (amount.compareTo(transaction.getAmount()) > 0) {
            throw filingError("AMOUNT_EXCEEDS_TRANSACTION",
                    "The disputed amount cannot exceed the transaction amount.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        DisputeReason parsedReason;
        try {
            parsedReason = DisputeReason.valueOf(reasonCode == null ? "" : reasonCode);
        } catch (IllegalArgumentException exception) {
            throw filingError("INVALID_REASON_CODE", "Choose one of the supported dispute reasons.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (description != null && description.length() > 500) {
            throw filingError("DESCRIPTION_TOO_LONG", "The description must be 500 characters or fewer.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (parsedReason == DisputeReason.FRAUD && amount.compareTo(transaction.getAmount()) != 0) {
            throw filingError("FRAUD_MUST_DISPUTE_FULL_AMOUNT",
                    "A fraud dispute must be for the full transaction amount.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String normalizedDescription = description == null || description.isBlank() ? null : description.trim();
        return disputes.save(new Dispute(transaction, amount, parsedReason, normalizedDescription));
    }

    @Transactional(readOnly = true)
    public Map<String, String> filingBlockReasons(List<CardTransaction> transactionsToCheck) {
        Map<String, String> reasons = new LinkedHashMap<>();
        for (CardTransaction transaction : transactionsToCheck) {
            FilingBlock filingBlock = filingBlock(transaction);
            if (filingBlock != null) {
                reasons.put(transaction.getId(), filingBlock.message());
            }
        }
        return reasons;
    }

    private FilingBlock filingBlock(CardTransaction transaction) {
        if (disputes.existsByTransaction_IdAndStatus(transaction.getId(), DisputeStatus.RESOLVED_APPROVED)) {
            return new FilingBlock("TRANSACTION_ALREADY_APPROVED",
                    "An approved dispute already exists for this transaction.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (disputes.existsByTransaction_IdAndStatusIn(transaction.getId(),
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW, DisputeStatus.AWAITING_CARDMEMBER))) {
            return new FilingBlock("ACTIVE_DISPUTE_EXISTS",
                    "This transaction already has an active dispute.", HttpStatus.CONFLICT);
        }
        if (LocalDate.now(clock).isAfter(transaction.getPostingDate().plusDays(120))) {
            return new FilingBlock("FILING_WINDOW_EXPIRED",
                    "This transaction is outside the 120-day filing window.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return null;
    }

    private DisputeFilingException filingError(String code, String message, HttpStatus status) {
        return new DisputeFilingException(code, message, status);
    }

    private record FilingBlock(String code, String message, HttpStatus status) {
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
