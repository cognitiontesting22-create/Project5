package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeAdjustment;
import com.cardbench.disputes.domain.DisputeAdjustmentKind;
import com.cardbench.disputes.domain.DisputeCreditState;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeAdjustmentRepository;
import com.cardbench.disputes.repository.DisputeEventRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Service
public class DisputeService {
    private final DisputeRepository disputes;
    private final DisputeAdjustmentRepository adjustments;
    private final DisputeEventRepository events;
    private final CardTransactionRepository transactions;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes, DisputeAdjustmentRepository adjustments,
                          DisputeEventRepository events,
                          CardTransactionRepository transactions, Clock clock) {
        this.disputes = disputes;
        this.adjustments = adjustments;
        this.events = events;
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

    @Transactional(readOnly = true)
    public List<DisputeEvent> history(Long id) {
        getDispute(id);
        return events.findAllByDispute_IdOrderByOccurredAtAscIdAsc(id);
    }

    @Transactional(readOnly = true)
    public List<DisputeEvent> historyForCardmember(String cardmemberId) {
        return events.findAllByDispute_Transaction_Card_Cardmember_IdAndInternalFalseOrderByOccurredAtAscIdAsc(
                cardmemberId);
    }

    @Transactional
    public Dispute fileDispute(String transactionId, BigDecimal amount, String reasonCode, String description) {
        return fileDispute(transactionId, amount, reasonCode, description, null);
    }

    @Transactional
    public Dispute fileDispute(
            String transactionId, BigDecimal amount, String reasonCode, String description, String actor) {
        actor = normalizeActor(actor);
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
        if (amount.stripTrailingZeros().scale() > 2) {
            throw filingError("AMOUNT_PRECISION_INVALID", "The disputed amount must be exact to the cent.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        amount = amount.setScale(2);
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
        Dispute dispute = disputes.save(new Dispute(transaction, amount, parsedReason, normalizedDescription));
        appendEvent(dispute, DisputeEventKind.FILED, actor,
                "Dispute filed for $" + amount.toPlainString() + " (" + parsedReason + ").", false);
        return dispute;
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
        return setStatus(id, status, resolutionNote, null);
    }

    @Transactional
    public Dispute setStatus(Long id, DisputeStatus status, String resolutionNote, String actor) {
        actor = normalizeActor(actor);
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
        DisputeStatus previousStatus = dispute.getStatus();
        dispute.transitionTo(status, status.isResolvedOutcome() ? resolutionNote.trim() : null);
        appendEvent(dispute, DisputeEventKind.STATUS_CHANGED, actor,
                "Status changed from " + previousStatus + " to " + status + ".", false);
        if (status.isResolvedOutcome()) {
            appendEvent(dispute, DisputeEventKind.NOTE, actor, "Resolution note: " + resolutionNote.trim(), false);
        }
        updateCredit(dispute, status, actor);
        return disputes.save(dispute);
    }

    @Transactional
    public DisputeEvent addNote(Long id, String text, boolean internal, String actor) {
        actor = normalizeActor(actor);
        Dispute dispute = getDispute(id);
        if (text == null || text.isBlank()) {
            throw new InvalidCaseNoteException("NOTE_REQUIRED", "A case note cannot be empty.");
        }
        if (text.length() > 1000) {
            throw new InvalidCaseNoteException("NOTE_TOO_LONG", "A case note must be 1000 characters or fewer.");
        }
        return appendEvent(dispute, DisputeEventKind.NOTE, actor, text.trim(), internal);
    }

    private void updateCredit(Dispute dispute, DisputeStatus status, String actor) {
        if (status == DisputeStatus.UNDER_REVIEW && dispute.getCreditState() == DisputeCreditState.NONE) {
            adjustments.save(new DisputeAdjustment(dispute, DisputeAdjustmentKind.PROVISIONAL_CREDIT,
                    dispute.getAmount(), LocalDate.now(clock)));
            dispute.setCreditState(DisputeCreditState.PROVISIONAL);
            appendEvent(dispute, DisputeEventKind.CREDIT_PLACED, actor,
                    "Provisional credit of $" + dispute.getAmount().toPlainString() + " placed.", false);
        } else if (status == DisputeStatus.RESOLVED_APPROVED
                && dispute.getCreditState() == DisputeCreditState.PROVISIONAL) {
            DisputeAdjustment credit = adjustments.findByDispute_IdAndKind(
                            dispute.getId(), DisputeAdjustmentKind.PROVISIONAL_CREDIT)
                    .orElseThrow(() -> new IllegalStateException("Provisional credit adjustment is missing"));
            credit.makePermanent();
            adjustments.save(credit);
            dispute.setCreditState(DisputeCreditState.PERMANENT);
            appendEvent(dispute, DisputeEventKind.CREDIT_MADE_PERMANENT, actor,
                    "Credit of $" + dispute.getAmount().toPlainString() + " made permanent.", false);
        } else if ((status == DisputeStatus.RESOLVED_DENIED || status == DisputeStatus.WITHDRAWN)
                && dispute.getCreditState() == DisputeCreditState.PROVISIONAL) {
            adjustments.save(new DisputeAdjustment(dispute, DisputeAdjustmentKind.REVERSAL,
                    dispute.getAmount(), LocalDate.now(clock)));
            dispute.setCreditState(DisputeCreditState.REVERSED);
            appendEvent(dispute, DisputeEventKind.CREDIT_REVERSED, actor,
                    "Provisional credit of $" + dispute.getAmount().toPlainString() + " reversed.", false);
        }
    }

    private DisputeEvent appendEvent(
            Dispute dispute, DisputeEventKind kind, String actor, String text, boolean internal) {
        return events.save(new DisputeEvent(dispute, Instant.now(clock), kind, actor, text, internal));
    }

    private String normalizeActor(String actor) {
        String normalizedActor = actor == null || actor.isBlank() ? "system" : actor.trim();
        if (normalizedActor.length() > 100) {
            throw new InvalidCaseNoteException("ACTOR_TOO_LONG", "An actor name must be 100 characters or fewer.");
        }
        return normalizedActor;
    }

    @Transactional
    public Dispute withdrawDisputeForCardmember(Long id, String cardmemberId) {
        return withdrawDisputeForCardmember(id, cardmemberId, null);
    }

    @Transactional
    public Dispute withdrawDisputeForCardmember(Long id, String cardmemberId, String actor) {
        Dispute dispute = getDispute(id);
        if (!dispute.getTransaction().getCard().getCardmember().getId().equals(cardmemberId)) {
            throw new ResourceNotFoundException("Dispute not found: " + id);
        }
        return setStatus(id, DisputeStatus.WITHDRAWN, null, actor);
    }
}
