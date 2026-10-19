package com.cardbench.disputes.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.Dispute;
import com.cardbench.disputes.domain.DisputeStatus;
import com.cardbench.disputes.domain.PaymentCard;

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
            String reason,
            DisputeStatus status,
            LocalDate postingDate) {
        static DisputeView from(Dispute dispute) {
            CardTransaction transaction = dispute.getTransaction();
            return new DisputeView(dispute.getId(), transaction.getId(), transaction.getMerchant(),
                    transaction.getCard().getCardmember().getName(), dispute.getAmount(), dispute.getReason(),
                    dispute.getStatus(), transaction.getPostingDate());
        }
    }
}
