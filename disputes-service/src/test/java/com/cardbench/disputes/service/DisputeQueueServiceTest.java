package com.cardbench.disputes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.repository.CardmemberRepository;
import com.cardbench.disputes.repository.DisputeEventRepository;
import com.cardbench.disputes.repository.DisputeRepository;
import com.cardbench.disputes.service.DisputeQueueService.Direction;
import com.cardbench.disputes.service.DisputeQueueService.QueueItem;
import com.cardbench.disputes.service.DisputeQueueService.QueueRequest;
import com.cardbench.disputes.service.DisputeQueueService.Sort;

@ExtendWith(MockitoExtension.class)
class DisputeQueueServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 20);

    @Mock
    private DisputeRepository disputes;
    @Mock
    private DisputeEventRepository events;
    @Mock
    private CardmemberRepository cardmembers;

    private DisputeQueueService service;
    private List<Dispute> testDisputes;
    private List<DisputeEvent> testEvents;
    private Dispute overdue;
    private Dispute dueSoon;
    private Dispute paused;
    private Dispute finalCase;

    @BeforeEach
    void setUp() {
        service = new DisputeQueueService(disputes, events, cardmembers,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        testDisputes = new ArrayList<>();
        testEvents = new ArrayList<>();
        overdue = dispute("member-01", "txn-1", "50.00", DisputeReason.FRAUD, DisputeStatus.OPEN);
        dueSoon = dispute("member-02", "txn-2", "20.00", DisputeReason.NOT_RECEIVED, DisputeStatus.UNDER_REVIEW);
        paused = dispute("member-01", "txn-3", "30.00", DisputeReason.FRAUD, DisputeStatus.AWAITING_CARDMEMBER);
        finalCase = dispute("member-02", "txn-4", "10.00", DisputeReason.NOT_RECEIVED, DisputeStatus.RESOLVED_DENIED);

        filing(overdue, "2026-05-01T08:00:00Z");
        filing(dueSoon, "2026-05-28T08:00:00Z");
        filing(paused, "2026-05-01T08:00:00Z");
        filing(finalCase, "2026-05-01T08:00:00Z");
        testEvents.add(event(paused, "2026-06-05T10:00:00Z", DisputeEventKind.STATUS_CHANGED,
                "Status changed from UNDER_REVIEW to AWAITING_CARDMEMBER."));
        org.mockito.Mockito.lenient().when(disputes.findAll()).thenReturn(testDisputes);
        org.mockito.Mockito.lenient().when(events.findAllByOrderByOccurredAtAscIdAsc()).thenReturn(testEvents);
    }

    @Test
    void dueDatesPauseForWholeAwaitingDaysAndOverdueExcludesFinalCases() {
        var page = service.queue(QueueRequest.defaults());

        assertEquals(LocalDate.of(2026, 5, 31), item(page.items(), overdue).dueDate());
        assertEquals(LocalDate.of(2026, 6, 27), item(page.items(), dueSoon).dueDate());
        assertEquals(LocalDate.of(2026, 6, 15), item(page.items(), paused).dueDate());
        assertEquals(2, page.summary().overdueCount());
        assertEquals(1, page.summary().dueNextSevenDays());
        assertEquals(DisputeStatus.RESOLVED_DENIED, finalCase.getStatus());
    }

    @Test
    void completedAwaitingIntervalsExtendTheDueDateByCalendarDays() {
        Instant filedAt = Instant.parse("2026-05-01T08:00:00Z");
        List<DisputeEvent> history = List.of(
                event(overdue, "2026-05-05T10:00:00Z", DisputeEventKind.STATUS_CHANGED,
                        "Status changed from UNDER_REVIEW to AWAITING_CARDMEMBER."),
                event(overdue, "2026-05-15T10:00:00Z", DisputeEventKind.STATUS_CHANGED,
                        "Status changed from AWAITING_CARDMEMBER to UNDER_REVIEW."));

        assertEquals(LocalDate.of(2026, 6, 10), DisputeQueueService.dueDate(filedAt, history, TODAY));
    }

    @Test
    void combinesFiltersAndSortsBothDirectionsBeforePaging() {
        when(cardmembers.existsById("member-01")).thenReturn(true);
        QueueRequest combined = service.parseRequest(
                List.of("OPEN", "AWAITING_CARDMEMBER"), "FRAUD", "member-01",
                null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1),
                "amount", "desc", 0, 20);
        var filtered = service.queue(combined);
        assertEquals(2, filtered.totalCount());
        assertEquals(overdue, filtered.items().get(0).dispute());
        assertEquals(paused, filtered.items().get(1).dispute());

        QueueRequest ascendingAmount = new QueueRequest(
                List.of(DisputeStatus.OPEN, DisputeStatus.AWAITING_CARDMEMBER), DisputeReason.FRAUD,
                "member-01", false, null, null, Sort.AMOUNT, Direction.ASC, 0, 20);
        assertEquals(paused, service.queue(ascendingAmount).items().get(0).dispute());
    }

    @Test
    void sortsAndPagesWithCorrectTotalCounts() {
        QueueRequest amountDescending = new QueueRequest(
                List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW, DisputeStatus.AWAITING_CARDMEMBER),
                null, null, false, null, null, Sort.AMOUNT, Direction.DESC, 0, 1);
        var firstPage = service.queue(amountDescending);
        assertEquals(3, firstPage.totalCount());
        assertEquals(3, firstPage.totalPages());
        assertEquals(0, firstPage.page());
        assertEquals(overdue, firstPage.items().get(0).dispute());

        QueueRequest secondPage = new QueueRequest(amountDescending.statuses(), null, null, false,
                null, null, Sort.AMOUNT, Direction.DESC, 1, 1);
        assertEquals(paused, service.queue(secondPage).items().get(0).dispute());
    }

    @Test
    void sortsByDueAndFiledDateInEitherDirection() {
        QueueRequest dueDescending = new QueueRequest(List.of(), null, null, false,
                null, null, Sort.DUE_DATE, Direction.DESC, 0, 20);
        assertEquals(dueSoon, service.queue(dueDescending).items().get(0).dispute());

        QueueRequest filedAscending = new QueueRequest(List.of(), null, null, false,
                null, null, Sort.FILED_DATE, Direction.ASC, 0, 20);
        List<QueueItem> ascending = service.queue(filedAscending).items();
        assertEquals(overdue, ascending.get(0).dispute());
        assertEquals(dueSoon, ascending.get(2).dispute());

        QueueRequest filedDescending = new QueueRequest(List.of(), null, null, false,
                null, null, Sort.FILED_DATE, Direction.DESC, 0, 20);
        assertEquals(dueSoon, service.queue(filedDescending).items().get(0).dispute());
    }

    @Test
    void overdueFilterReturnsOnlyActiveCasesPastTheirAdjustedDueDate() {
        QueueRequest overdueOnly = service.parseRequest(null, null, null, "true",
                null, null, null, null, null, null);

        var result = service.queue(overdueOnly);

        assertEquals(2, result.totalCount());
        assertEquals(true, result.items().stream().allMatch(QueueItem::overdue));
        assertEquals(false, result.items().stream().anyMatch(item -> item.dispute() == finalCase));
    }

    @Test
    void rejectsUnknownFiltersSortsAndInvalidPageValues() {
        assertThrows(InvalidQueueRequestException.class,
                () -> service.parseRequest(List.of("PENDING"), null, null, null,
                        null, null, null, null, null, null));
        assertThrows(InvalidQueueRequestException.class,
                () -> service.parseRequest(null, "UNKNOWN", null, null,
                        null, null, null, null, null, null));
        assertThrows(InvalidQueueRequestException.class,
                () -> service.parseRequest(null, null, null, null,
                        null, null, "merchant", null, null, null));
        assertThrows(InvalidQueueRequestException.class,
                () -> service.parseRequest(null, null, null, null,
                        null, null, null, "sideways", null, null));
        assertThrows(InvalidQueueRequestException.class,
                () -> service.queue(new QueueRequest(List.of(), null, null, false, null, null,
                        Sort.DUE_DATE, Direction.ASC, 0, 101)));
        assertThrows(InvalidQueueRequestException.class,
                () -> service.queue(new QueueRequest(List.of(), null, null, false,
                        LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1),
                        Sort.DUE_DATE, Direction.ASC, 0, 20)));
    }

    private QueueItem item(List<QueueItem> items, Dispute dispute) {
        return items.stream().filter(item -> item.dispute() == dispute).findFirst().orElseThrow();
    }

    private Dispute dispute(String memberId, String transactionId, String amount,
                            DisputeReason reason, DisputeStatus status) {
        Cardmember member = new Cardmember(memberId, memberId, memberId + "@example.test");
        PaymentCard card = new PaymentCard("card-" + transactionId, "Card", member);
        CardTransaction transaction = new CardTransaction(transactionId, "Merchant", "Retail",
                new BigDecimal(amount), TODAY, card);
        Dispute dispute = new Dispute(transaction, new BigDecimal(amount), reason, null);
        if (status != DisputeStatus.OPEN) {
            dispute.transitionTo(status, null);
        }
        if (testDisputes == null) {
            testDisputes = new ArrayList<>();
        }
        testDisputes.add(dispute);
        return dispute;
    }

    private void filing(Dispute dispute, String occurredAt) {
        testEvents.add(event(dispute, occurredAt, DisputeEventKind.FILED, "Dispute filed."));
    }

    private DisputeEvent event(Dispute dispute, String occurredAt, DisputeEventKind kind, String text) {
        return new DisputeEvent(dispute, Instant.parse(occurredAt), kind, "analyst", text, false);
    }
}
