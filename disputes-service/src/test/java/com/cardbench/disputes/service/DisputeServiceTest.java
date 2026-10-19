package com.cardbench.disputes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {
    @Mock
    private DisputeRepository disputes;
    @Mock
    private CardTransactionRepository transactions;
    @InjectMocks
    private DisputeService service;

    private CardTransaction transaction;

    @BeforeEach
    void setUp() {
        Cardmember member = new Cardmember("member-01", "Avery Morgan", "member-01@cardbench.example");
        PaymentCard card = new PaymentCard("card-01", "Everyday card 01", member);
        transaction = new CardTransaction("txn-001", "Northstar Market", "Groceries",
                new BigDecimal("12.34"), LocalDate.parse("2026-01-15"), card);
    }

    @Test
    void filesRepeatedDisputesForAnAmountAboveTheTransaction() {
        when(transactions.findById("txn-001")).thenReturn(Optional.of(transaction));
        when(disputes.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispute first = service.fileDispute("txn-001", new BigDecimal("999.99"), "Anything at all");
        Dispute second = service.fileDispute("txn-001", new BigDecimal("999.99"), "Anything at all");

        assertEquals(new BigDecimal("999.99"), first.getAmount());
        assertEquals(DisputeStatus.OPEN, first.getStatus());
        assertEquals(first.getReason(), second.getReason());
        verify(disputes, times(2)).save(any(Dispute.class));
    }

    @Test
    void enforcesLifecycleAndStoresResolutionNote() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), "Not mine");
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));
        when(disputes.save(dispute)).thenReturn(dispute);

        assertEquals(DisputeStatus.UNDER_REVIEW,
                service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null).getStatus());
        assertEquals(DisputeStatus.AWAITING_CARDMEMBER,
                service.setStatus(7L, DisputeStatus.AWAITING_CARDMEMBER, null).getStatus());
        assertEquals(DisputeStatus.UNDER_REVIEW,
                service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null).getStatus());
        assertEquals(DisputeStatus.RESOLVED_APPROVED,
                service.setStatus(7L, DisputeStatus.RESOLVED_APPROVED, "Refund issued").getStatus());
        assertEquals("Refund issued", dispute.getResolutionNote());
        assertThrows(InvalidDisputeTransitionException.class,
                () -> service.setStatus(7L, DisputeStatus.OPEN, null));
        assertEquals(DisputeStatus.RESOLVED_APPROVED, dispute.getStatus());
        verify(disputes, times(4)).save(dispute);
    }

    @Test
    void rejectsSkippingReviewAndResolutionWithoutNoteWithoutChangingState() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), "Not mine");
        when(disputes.findDetailedById(7L)).thenReturn(Optional.of(dispute));

        assertThrows(InvalidDisputeTransitionException.class,
                () -> service.setStatus(7L, DisputeStatus.RESOLVED_DENIED, "Merchant wins"));
        assertEquals(DisputeStatus.OPEN, dispute.getStatus());

        service.setStatus(7L, DisputeStatus.UNDER_REVIEW, null);
        assertThrows(InvalidResolutionNoteException.class,
                () -> service.setStatus(7L, DisputeStatus.RESOLVED_DENIED, "  "));
        assertEquals(DisputeStatus.UNDER_REVIEW, dispute.getStatus());
        assertEquals(null, dispute.getResolutionNote());
        verify(disputes, times(1)).save(dispute);
    }

    @Test
    void allowsWithdrawalBeforeAnOutcomeAndTreatsItAsFinal() {
        Dispute dispute = new Dispute(transaction, new BigDecimal("12.34"), "Not mine");
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
                () -> service.fileDispute("missing", BigDecimal.ONE, "Reason"));
    }
}
