package com.cardbench.disputes.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeAdjustmentKind;
import com.cardbench.disputes.domain.DisputeCreditState;
import com.cardbench.disputes.domain.DisputeEvent;
import com.cardbench.disputes.domain.DisputeEventKind;
import com.cardbench.disputes.domain.DisputeReason;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.service.CardAccountService.CardAccount;
import com.cardbench.disputes.service.CardAccountService.AccountAdjustment;
import com.cardbench.disputes.service.DisputeQueueService.QueuePage;
import com.cardbench.disputes.service.DisputeQueueService.QueueItem;
import com.cardbench.disputes.service.DisputeQueueService.QueueSummary;

public final class ApiViews {
    private ApiViews() {
    }

    public record CardmemberView(String id, String name, String email) {
        static CardmemberView from(Cardmember member) {
            return new CardmemberView(member.getId(), member.getName(), member.getEmail());
        }
    }

    public record CardView(String id, String label, String cardmemberId, String cardmemberName) {
        static CardView from(PaymentCard card) {
            return new CardView(card.getId(), card.getLabel(),
                    card.getCardmember().getId(), card.getCardmember().getName());
        }
    }

    public record TransactionView(
            String id,
            String cardId,
            String cardmemberId,
            String merchant,
            String category,
            BigDecimal amount,
            LocalDate postingDate) {
        static TransactionView from(CardTransaction transaction) {
            PaymentCard card = transaction.getCard();
            return new TransactionView(transaction.getId(), card.getId(), card.getCardmember().getId(),
                    transaction.getMerchant(), transaction.getCategory(), transaction.getAmount(),
                    transaction.getPostingDate());
        }
    }

    public record DisputeView(
            Long id,
            String transactionId,
            String merchant,
            String cardmemberName,
            BigDecimal amount,
            DisputeReason reasonCode,
            String description,
            DisputeStatus status,
            String resolutionNote,
            LocalDate postingDate,
            DisputeCreditState creditState) {
        static DisputeView from(Dispute dispute) {
            CardTransaction transaction = dispute.getTransaction();
            return new DisputeView(dispute.getId(), transaction.getId(), transaction.getMerchant(),
                    transaction.getCard().getCardmember().getName(), dispute.getAmount(), dispute.getReasonCode(),
                    dispute.getDescription(),
                    dispute.getStatus(), dispute.getResolutionNote(), transaction.getPostingDate(),
                    dispute.getCreditState());
        }
    }

    public record CardAccountView(
            String cardId,
            BigDecimal postedTransactionsTotal,
            BigDecimal creditsInForceTotal,
            BigDecimal balanceOwed,
            java.util.List<AccountAdjustmentView> adjustments) {
        static CardAccountView from(String cardId, CardAccount account) {
            return new CardAccountView(cardId, account.postedTransactionsTotal(), account.creditsInForceTotal(),
                    account.balanceOwed(), account.adjustments().stream().map(AccountAdjustmentView::from).toList());
        }
    }

    public record AccountAdjustmentView(
            Long disputeId,
            DisputeAdjustmentKind kind,
            BigDecimal amount,
            LocalDate date) {
        static AccountAdjustmentView from(AccountAdjustment adjustment) {
            return new AccountAdjustmentView(adjustment.disputeId(), adjustment.kind(),
                    adjustment.amount(), adjustment.date());
        }
    }

    public record DisputeEventView(
            Long id,
            Instant occurredAt,
            DisputeEventKind kind,
            String actor,
            String text,
            boolean internal) {
        static DisputeEventView from(DisputeEvent event) {
            return new DisputeEventView(event.getId(), event.getOccurredAt(),
                    event.getKind(), event.getActor(), event.getText(), event.isInternal());
        }
    }

    public record QueuePageView(
            List<QueueItemView> items,
            long totalCount,
            int totalPages,
            int page,
            int pageSize,
            QueueSummaryView summary) {
        static QueuePageView from(QueuePage page) {
            QueueSummary summary = page.summary();
            return new QueuePageView(page.items().stream().map(QueueItemView::from).toList(),
                    page.totalCount(), page.totalPages(), page.page(), page.pageSize(),
                    new QueueSummaryView(summary.statusCounts(), summary.overdueCount(), summary.dueNextSevenDays()));
        }
    }

    public record QueueSummaryView(
            Map<DisputeStatus, Long> statusCounts,
            long overdueCount,
            long dueNextSevenDays) {
    }

    public record QueueItemView(
            DisputeView dispute,
            Instant filedAt,
            LocalDate filedDate,
            LocalDate dueDate,
            boolean overdue) {
        static QueueItemView from(QueueItem item) {
            return new QueueItemView(DisputeView.from(item.dispute()), item.filedAt(),
                    item.filedDate(), item.dueDate(), item.overdue());
        }
    }

    public record DisputeReasonView(DisputeReason code, String label) {
        static DisputeReasonView from(DisputeReason reason) {
            return new DisputeReasonView(reason, reason.getDisplayName());
        }
    }
}
