package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.repository.CardmemberRepository;
import com.cardbench.disputes.repository.DisputeEventRepository;
import com.cardbench.disputes.repository.DisputeRepository;

@Service
public class DisputeQueueService {
    private static final Set<DisputeStatus> ACTIVE_STATUSES = Set.of(
            DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW, DisputeStatus.AWAITING_CARDMEMBER);

    private final DisputeRepository disputes;
    private final DisputeEventRepository events;
    private final CardmemberRepository cardmembers;
    private final Clock clock;

    public DisputeQueueService(
            DisputeRepository disputes, DisputeEventRepository events, CardmemberRepository cardmembers, Clock clock) {
        this.disputes = disputes;
        this.events = events;
        this.cardmembers = cardmembers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QueuePage queue(QueueRequest request) {
        validate(request);
        List<Dispute> allDisputes = disputes.findAll();
        Map<Dispute, List<DisputeEvent>> eventMap = new java.util.IdentityHashMap<>();
        for (DisputeEvent event : events.findAllByOrderByOccurredAtAscIdAsc()) {
            eventMap.computeIfAbsent(event.getDispute(), ignored -> new ArrayList<>()).add(event);
        }

        LocalDate today = LocalDate.now(clock);
        List<QueueItem> allItems = allDisputes.stream()
                .map(dispute -> queueItem(dispute, eventMap.getOrDefault(dispute, List.of()), today))
                .toList();
        List<QueueItem> filtered = allItems.stream()
                .filter(item -> request.statuses().isEmpty()
                        ? ACTIVE_STATUSES.contains(item.dispute().getStatus())
                        : request.statuses().contains(item.dispute().getStatus()))
                .filter(item -> request.reasonCode() == null
                        || item.dispute().getReasonCode() == request.reasonCode())
                .filter(item -> request.cardmemberId() == null
                        || item.dispute().getTransaction().getCard().getCardmember().getId()
                                .equals(request.cardmemberId()))
                .filter(item -> !request.overdueOnly() || item.overdue())
                .filter(item -> request.filedFrom() == null || !item.filedDate().isBefore(request.filedFrom()))
                .filter(item -> request.filedTo() == null || !item.filedDate().isAfter(request.filedTo()))
                .sorted(comparator(request.sort(), request.direction()))
                .toList();

        EnumMap<DisputeStatus, Long> statusCounts = new EnumMap<>(DisputeStatus.class);
        for (DisputeStatus status : DisputeStatus.values()) {
            statusCounts.put(status, allItems.stream().filter(item -> item.dispute().getStatus() == status).count());
        }
        long overdueCount = allItems.stream().filter(QueueItem::overdue).count();
        long dueNextSevenDays = allItems.stream()
                .filter(item -> !item.dispute().getStatus().isFinal())
                .filter(item -> !item.dueDate().isBefore(today) && !item.dueDate().isAfter(today.plusDays(7)))
                .count();

        int first = (int) Math.min((long) request.page() * request.pageSize(), filtered.size());
        int last = Math.min(first + request.pageSize(), filtered.size());
        int totalPages = (filtered.size() + request.pageSize() - 1) / request.pageSize();
        return new QueuePage(filtered.subList(first, last), filtered.size(), totalPages,
                request.page(), request.pageSize(),
                new QueueSummary(statusCounts, overdueCount, dueNextSevenDays));
    }

    public QueueRequest parseRequest(
            List<String> statuses,
            String reasonCode,
            String cardmemberId,
            String overdue,
            LocalDate filedFrom,
            LocalDate filedTo,
            String sort,
            String direction,
            Integer page,
            Integer pageSize) {
        cardmemberId = cardmemberId == null || cardmemberId.isBlank() ? null : cardmemberId.trim();
        List<DisputeStatus> parsedStatuses = statuses == null ? List.of() : statuses.stream().map(value -> {
            try {
                return DisputeStatus.valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new InvalidQueueRequestException("Unknown dispute status filter: " + value);
            }
        }).toList();
        DisputeReason parsedReason = null;
        if (reasonCode != null && !reasonCode.isBlank()) {
            try {
                parsedReason = DisputeReason.valueOf(reasonCode);
            } catch (IllegalArgumentException exception) {
                throw new InvalidQueueRequestException("Unknown dispute reason filter: " + reasonCode);
            }
        }
        boolean parsedOverdue = false;
        if (overdue != null && !overdue.isBlank()) {
            if (overdue.equalsIgnoreCase("true")) {
                parsedOverdue = true;
            } else if (!overdue.equalsIgnoreCase("false")) {
                throw new InvalidQueueRequestException("Unknown overdue filter: " + overdue);
            }
        }
        return new QueueRequest(parsedStatuses, parsedReason, cardmemberId, parsedOverdue, filedFrom, filedTo,
                sort == null || sort.isBlank() ? Sort.DUE_DATE : Sort.parse(sort),
                direction == null || direction.isBlank() ? Direction.ASC : Direction.parse(direction),
                page == null ? 0 : page,
                pageSize == null ? 20 : pageSize);
    }

    public static LocalDate dueDate(Instant filedAt, List<DisputeEvent> events, LocalDate today) {
        LocalDate filedDate = filedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dueDate = filedDate.plusDays(30);
        Instant awaitingSince = null;
        for (DisputeEvent event : events) {
            if (event.getKind() != DisputeEventKind.STATUS_CHANGED) {
                continue;
            }
            String text = event.getText();
            if (text.startsWith("Status changed from ") && text.contains(" to AWAITING_CARDMEMBER.")) {
                awaitingSince = event.getOccurredAt();
            } else if (awaitingSince != null && text.startsWith("Status changed from AWAITING_CARDMEMBER to ")) {
                dueDate = dueDate.plusDays(ChronoUnit.DAYS.between(
                        awaitingSince.atZone(ZoneOffset.UTC).toLocalDate(),
                        event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate()));
                awaitingSince = null;
            }
        }
        if (awaitingSince != null) {
            dueDate = dueDate.plusDays(ChronoUnit.DAYS.between(
                    awaitingSince.atZone(ZoneOffset.UTC).toLocalDate(), today));
        }
        return dueDate;
    }

    private QueueItem queueItem(Dispute dispute, List<DisputeEvent> history, LocalDate today) {
        DisputeEvent filing = history.stream()
                .filter(event -> event.getKind() == DisputeEventKind.FILED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Dispute " + dispute.getId() + " has no filing event"));
        LocalDate filedDate = filing.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dueDate = dueDate(filing.getOccurredAt(), history, today);
        boolean overdue = !dispute.getStatus().isFinal() && today.isAfter(dueDate);
        return new QueueItem(dispute, filing.getOccurredAt(), filedDate, dueDate, overdue);
    }

    private Comparator<QueueItem> comparator(Sort sort, Direction direction) {
        Comparator<QueueItem> comparator = switch (sort) {
            case DUE_DATE -> Comparator.comparing(QueueItem::dueDate);
            case FILED_DATE -> Comparator.comparing(QueueItem::filedAt);
            case AMOUNT -> Comparator.comparing(item -> item.dispute().getAmount());
        };
        if (direction == Direction.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(item -> item.dispute().getTransaction().getId());
    }

    private void validate(QueueRequest request) {
        if (request.page() < 0) {
            throw new InvalidQueueRequestException("Page number must be zero or greater.");
        }
        if (request.pageSize() < 1 || request.pageSize() > 100) {
            throw new InvalidQueueRequestException("Page size must be between 1 and 100.");
        }
        if (request.filedFrom() != null && request.filedTo() != null
                && request.filedFrom().isAfter(request.filedTo())) {
            throw new InvalidQueueRequestException("Filed-from date must not be after filed-to date.");
        }
        if (request.cardmemberId() != null && !cardmembers.existsById(request.cardmemberId())) {
            throw new InvalidQueueRequestException("Unknown cardmember filter.");
        }
    }

    public enum Sort {
        DUE_DATE("dueDate"),
        FILED_DATE("filedDate"),
        AMOUNT("amount");

        private final String parameter;

        Sort(String parameter) {
            this.parameter = parameter;
        }

        public String parameter() {
            return parameter;
        }

        public static Sort parse(String value) {
            for (Sort sort : values()) {
                if (sort.parameter.equalsIgnoreCase(value)) {
                    return sort;
                }
            }
            throw new InvalidQueueRequestException("Unknown queue sort: " + value);
        }
    }

    public enum Direction {
        ASC,
        DESC;

        public static Direction parse(String value) {
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidQueueRequestException("Unknown queue direction: " + value);
            }
        }
    }

    public record QueueRequest(
            List<DisputeStatus> statuses,
            DisputeReason reasonCode,
            String cardmemberId,
            boolean overdueOnly,
            LocalDate filedFrom,
            LocalDate filedTo,
            Sort sort,
            Direction direction,
            int page,
            int pageSize) {
        public QueueRequest {
            statuses = statuses == null ? List.of() : List.copyOf(statuses);
        }

        public static QueueRequest defaults() {
            return new QueueRequest(List.of(), null, null, false, null, null,
                    Sort.DUE_DATE, Direction.ASC, 0, 20);
        }
    }

    public record QueueItem(
            Dispute dispute,
            Instant filedAt,
            LocalDate filedDate,
            LocalDate dueDate,
            boolean overdue) {
    }

    public record QueueSummary(Map<DisputeStatus, Long> statusCounts, long overdueCount, long dueNextSevenDays) {
    }

    public record QueuePage(
            List<QueueItem> items,
            long totalCount,
            int totalPages,
            int page,
            int pageSize,
            QueueSummary summary) {
    }
}
