package com.cardbench.disputes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeAdjustment;
import com.cardbench.disputes.domain.DisputeAdjustmentKind;
import com.cardbench.disputes.domain.DisputeCreditState;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.config.DisputeHistoryInitializer;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeAdjustmentRepository;
import com.cardbench.disputes.repository.DisputeEventRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-01-15");

    @Mock
    private DisputeRepository disputes;
    @Mock
    private DisputeAdjustmentRepository adjustments;
    @Mock
    private DisputeEventRepository events;
    @Mock
    private CardTransactionRepository transactions;
    private DisputeService service;
    private CardTransaction transaction;

    @BeforeEach
    void setUp() {
        Cardmember member = new Cardmember("member-01", "Avery Morgan", "member-01@cardbench.example");
        PaymentCard card = new PaymentCard("card-01", "Everyday card 01", member);
        transaction = new CardTransaction("txn-001", "Northstar Market", "Groceries",
                new BigDecimal("12.34"), TODAY, card);
        service = new DisputeService(disputes, adjustments, events, transactions,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void acceptsAmountsGreaterThanZeroAndUpToTheTransactionAmount() {
        stubOriginalTransaction();
        when(disputes.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute partial = service.fileDispute("txn-001", new BigDecimal("1.00"), "NOT_RECEIVED", null);
        Dispute full = service.fileDispute("txn-001", new BigDecimal("12.34"), "FRAUD", null);

        assertEquals(new BigDecimal("1.00"), partial.getAmount());
        assertEquals(new BigDecimal("12.34"), full.getAmount());
        assertEquals(DisputeStatus.OPEN, full.getStatus());
        org.mockito.ArgumentCaptor<DisputeEvent> filingEvents =
                org.mockito.ArgumentCaptor.forClass(DisputeEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(filingEvents.capture());
        assertEquals(DisputeEventKind.FILED, filingEvents.getAllValues().get(0).getKind());
        assertEquals("system", filingEvents.getAllValues().get(0).getActor());
        assertEquals(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC),
                filingEvents.getAllValues().get(0).getOccurredAt());
    }

    @Test
    void rejectsZeroAndNegativeAmountsWithoutSaving() {
        assertFilingError("AMOUNT_MUST_BE_POSITIVE", BigDecimal.ZERO, "NOT_RECEIVED", null);
        assertFilingError("AMOUNT_MUST_BE_POSITIVE", new BigDecimal("-0.01"), "NOT_RECEIVED", null);
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void rejectsAmountsThatCannotBeRepresentedToTheCent() {
        assertFilingError("AMOUNT_PRECISION_INVALID", new BigDecimal("1.001"), "NOT_RECEIVED", null);
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void rejectsAmountsAboveTheTransactionWithoutSaving() {
        assertFilingError("AMOUNT_EXCEEDS_TRANSACTION", new BigDecimal("12.35"), "NOT_RECEIVED", null);
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void permitsFilingOnDay120AndRejectsDay121() {
        CardTransaction day120 = transactionWithPostingDate("txn-day-120", TODAY.minusDays(120));
        CardTransaction day121 = transactionWithPostingDate("txn-day-121", TODAY.minusDays(121));
        when(transactions.findById("txn-day-120")).thenReturn(Optional.of(day120));
        when(transactions.findById("txn-day-121")).thenReturn(Optional.of(day121));
        when(disputes.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(DisputeStatus.OPEN, service.fileDispute(
                "txn-day-120", BigDecimal.ONE, "NOT_RECEIVED", null).getStatus());
        DisputeFilingException exception = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-day-121", BigDecimal.ONE, "NOT_RECEIVED", null));
        assertEquals("FILING_WINDOW_EXPIRED", exception.getCode());
        verify(disputes).save(any(Dispute.class));
    }

    @Test
    void rejectsUnknownReasonsAndDescriptionsOver500Characters() {
        assertFilingError("INVALID_REASON_CODE", BigDecimal.ONE, "OTHER", null);
        assertFilingError("DESCRIPTION_TOO_LONG", BigDecimal.ONE, "NOT_RECEIVED", "x".repeat(501));
        DisputeFilingException missingReason = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-001", BigDecimal.ONE, null, null));
        assertEquals("INVALID_REASON_CODE", missingReason.getCode());
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void acceptsDescriptionAt500CharactersAndStoresIt() {
        stubOriginalTransaction();
        String description = "x".repeat(500);
        when(disputes.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute dispute = service.fileDispute("txn-001", BigDecimal.ONE, "NOT_RECEIVED", description);

        assertEquals(description, dispute.getDescription());
    }

    @Test
    void fraudRequiresTheFullTransactionAmount() {
        stubOriginalTransaction();
        DisputeFilingException exception = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-001", BigDecimal.ONE, "FRAUD", null));
        assertEquals("FRAUD_MUST_DISPUTE_FULL_AMOUNT", exception.getCode());
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void activeDisputesConflictButWithdrawnAndDeniedDisputesMayBeRefiled() {
        stubOriginalTransaction();
        when(disputes.existsByTransaction_IdAndStatusIn("txn-001",
                java.util.List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW,
                        DisputeStatus.AWAITING_CARDMEMBER))).thenReturn(true);
        DisputeFilingException active = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-001", BigDecimal.ONE, "NOT_RECEIVED", null));
        assertEquals("ACTIVE_DISPUTE_EXISTS", active.getCode());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, active.getStatus());
        verify(disputes, never()).save(any(Dispute.class));

        when(disputes.existsByTransaction_IdAndStatusIn("txn-001",
                java.util.List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW,
                        DisputeStatus.AWAITING_CARDMEMBER))).thenReturn(false);
        when(disputes.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(DisputeStatus.OPEN,
                service.fileDispute("txn-001", BigDecimal.ONE, "NOT_RECEIVED", null).getStatus());
    }

    @Test
    void approvedDisputesPermanentlyPreventRefiling() {
        stubOriginalTransaction();
        when(disputes.existsByTransaction_IdAndStatus("txn-001", DisputeStatus.RESOLVED_APPROVED))
                .thenReturn(true);

        DisputeFilingException exception = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-001", BigDecimal.ONE, "NOT_RECEIVED", null));

        assertEquals("TRANSACTION_ALREADY_APPROVED", exception.getCode());
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        verify(disputes, never()).save(any(Dispute.class));
    }

    @Test
    void enforcesLifecycleAndStoresResolutionNote() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        assertEquals(DisputeStatus.UNDER_REVIEW,
                service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null).getStatus());
        assertEquals(DisputeCreditState.PROVISIONAL, dispute.getCreditState());
        org.mockito.ArgumentCaptor<DisputeEvent> statusEvents =
                org.mockito.ArgumentCaptor.forClass(DisputeEvent.class);
        verify(events, org.mockito.Mockito.times(2)).save(statusEvents.capture());
        assertEquals(DisputeEventKind.STATUS_CHANGED, statusEvents.getAllValues().get(0).getKind());
        assertEquals("Status changed from OPEN to UNDER_REVIEW.", statusEvents.getAllValues().get(0).getText());
        assertEquals(DisputeEventKind.CREDIT_PLACED, statusEvents.getAllValues().get(1).getKind());
        assertEquals(DisputeStatus.AWAITING_CARDMEMBER,
                service.setStatus(7L, DisputeStatus.AWAITING_CARDMEMBER, null).getStatus());
        assertEquals(DisputeStatus.UNDER_REVIEW,
                service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null).getStatus());
        verify(events, org.mockito.Mockito.times(4)).save(any(DisputeEvent.class));
        verify(adjustments).save(any(DisputeAdjustment.class));
        verify(adjustments, never()).save(org.mockito.ArgumentMatchers.argThat(
                adjustment -> adjustment.getKind() == DisputeAdjustmentKind.REVERSAL));
        when(adjustments.findByDispute_IdAndKind(
                org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.eq(DisputeAdjustmentKind.PROVISIONAL_CREDIT)))
                .thenReturn(Optional.of(new DisputeAdjustment(dispute, DisputeAdjustmentKind.PROVISIONAL_CREDIT,
                        new BigDecimal("12.34"), TODAY)));
        assertEquals(DisputeStatus.RESOLVED_APPROVED,
                service.setStatus(7L, DisputeStatus.RESOLVED_APPROVED, "Refund issued").getStatus());
        assertEquals(DisputeCreditState.PERMANENT, dispute.getCreditState());
        assertEquals("Refund issued", dispute.getResolutionNote());
        assertThrows(InvalidDisputeTransitionException.class,
                () -> service.setStatus(7L, DisputeStatus.OPEN, null));
        assertEquals(DisputeStatus.RESOLVED_APPROVED, dispute.getStatus());
    }

    @Test
    void repeatedReviewIssuesOneCreditAndDeniedOutcomeReversesItOnlyOnce() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null);
        service.setStatus(7L, DisputeStatus.AWAITING_CARDMEMBER, null);
        service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null);

        org.mockito.ArgumentCaptor<DisputeAdjustment> credit =
                org.mockito.ArgumentCaptor.forClass(DisputeAdjustment.class);
        verify(adjustments, org.mockito.Mockito.times(1)).save(credit.capture());
        assertEquals(DisputeAdjustmentKind.PROVISIONAL_CREDIT, credit.getValue().getKind());
        assertEquals(new BigDecimal("12.34"), credit.getValue().getAmount());
        assertEquals(TODAY, credit.getValue().getAdjustmentDate());

        service.setStatus(7L, DisputeStatus.RESOLVED_DENIED, "Merchant evidence");
        assertEquals(DisputeCreditState.REVERSED, dispute.getCreditState());
        org.mockito.ArgumentCaptor<DisputeAdjustment> allAdjustments =
                org.mockito.ArgumentCaptor.forClass(DisputeAdjustment.class);
        verify(adjustments, org.mockito.Mockito.times(2)).save(allAdjustments.capture());
        DisputeAdjustment reversal = allAdjustments.getAllValues().get(1);
        assertEquals(DisputeAdjustmentKind.REVERSAL, reversal.getKind());
        assertEquals(new BigDecimal("12.34"), reversal.getAmount());
        assertEquals(TODAY, reversal.getAdjustmentDate());
    }

    @Test
    void withdrawingAnOpenDisputeDoesNotCreateACreditOrReversal() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        service.setStatus(7L, DisputeStatus.WITHDRAWN, null);

        assertEquals(DisputeCreditState.NONE, dispute.getCreditState());
        verify(adjustments, never()).save(any(DisputeAdjustment.class));
    }

    @Test
    void withdrawingAfterReviewReversesTheCreditOnce() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null);
        service.setStatus(7L, DisputeStatus.WITHDRAWN, null);

        assertEquals(DisputeCreditState.REVERSED, dispute.getCreditState());
        org.mockito.ArgumentCaptor<DisputeAdjustment> saved =
                org.mockito.ArgumentCaptor.forClass(DisputeAdjustment.class);
        verify(adjustments, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(DisputeAdjustmentKind.PROVISIONAL_CREDIT, saved.getAllValues().get(0).getKind());
        assertEquals(DisputeAdjustmentKind.REVERSAL, saved.getAllValues().get(1).getKind());
    }

    @Test
    void rejectsSkippingReviewAndResolutionWithoutNoteWithoutChangingState() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));

        assertThrows(InvalidDisputeTransitionException.class,
                () -> service.setStatus(7L, DisputeStatus.RESOLVED_DENIED, "Merchant wins"));
        assertEquals(DisputeStatus.OPEN, dispute.getStatus());
        verify(events, never()).save(any(DisputeEvent.class));

        service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null);
        assertThrows(InvalidResolutionNoteException.class,
                () -> service.setStatus(7L, DisputeStatus.RESOLVED_DENIED, "  "));
        assertEquals(DisputeStatus.UNDER_REVIEW, dispute.getStatus());
        assertNull(dispute.getResolutionNote());
    }

    @Test
    void caseNotesAreValidatedAndSavedWithActorAndPrivacyFlag() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(events.save(any(DisputeEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(InvalidCaseNoteException.class, () -> service.addNote(7L, "  ", false, "Analyst"));
        assertThrows(InvalidCaseNoteException.class, () -> service.addNote(7L, "x".repeat(1001), false, "Analyst"));
        verify(events, never()).save(any(DisputeEvent.class));

        DisputeEvent note = service.addNote(7L, "  Merchant contacted  ", true, "  Sam Analyst  ");
        assertEquals(DisputeEventKind.NOTE, note.getKind());
        assertEquals("Merchant contacted", note.getText());
        assertEquals("Sam Analyst", note.getActor());
        assertEquals(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), note.getOccurredAt());
        assertEquals(true, note.isInternal());
    }

    @Test
    void seededDisputesReceiveAOneTimeSystemFilingEvent() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findAll()).thenReturn(java.util.List.of(dispute));
        when(events.existsByDispute_IdAndKind(null, DisputeEventKind.FILED)).thenReturn(false);

        new DisputeHistoryInitializer(disputes, events,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)).run();

        org.mockito.ArgumentCaptor<DisputeEvent> event =
                org.mockito.ArgumentCaptor.forClass(DisputeEvent.class);
        verify(events).save(event.capture());
        assertEquals(DisputeEventKind.FILED, event.getValue().getKind());
        assertEquals("system", event.getValue().getActor());
        assertEquals(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), event.getValue().getOccurredAt());
    }

    @Test
    void allowsWithdrawalBeforeAnOutcomeAndTreatsItAsFinal() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), DisputeReason.NOT_RECEIVED, null);
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        service.setStatus(7L, DisputeStatus.WITHDRAWN, null);
        assertThrows(InvalidDisputeTransitionException.class,
                () -> service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null));
        assertEquals(DisputeStatus.WITHDRAWN, dispute.getStatus());
    }

    @Test
    void reportsMissingTransactions() {
        when(transactions.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.fileDispute("missing", BigDecimal.ONE, "NOT_RECEIVED", null));
    }

    private void assertFilingError(String expectedCode, BigDecimal amount, String reasonCode, String description) {
        stubOriginalTransaction();
        DisputeFilingException exception = assertThrows(DisputeFilingException.class,
                () -> service.fileDispute("txn-001", amount, reasonCode, description));
        assertEquals(expectedCode, exception.getCode());
    }

    private CardTransaction transactionWithPostingDate(String id, LocalDate postingDate) {
        return new CardTransaction(id, transaction.getMerchant(), transaction.getCategory(), transaction.getAmount(),
                postingDate, transaction.getCard());
    }

    private void stubOriginalTransaction() {
        when(transactions.findById("txn-001")).thenReturn(Optional.of(transaction));
    }
}
