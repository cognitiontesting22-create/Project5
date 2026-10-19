package com.cardbench.disputes.service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.DisputeAdjustmentKind;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.DisputeAdjustmentRepository;

@Service
@Transactional(readOnly = true)
public class CardAccountService {
    private final CardTransactionRepository transactions;
    private final DisputeAdjustmentRepository adjustments;

    public CardAccountService(CardTransactionRepository transactions, DisputeAdjustmentRepository adjustments) {
        this.transactions = transactions;
        this.adjustments = adjustments;
    }

    public CardAccount getAccount(String cardId) {
        BigDecimal postedTotal = transactions.findByCardId(cardId).stream()
                .map(transaction -> transaction.getAmount().setScale(2))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        List<AccountAdjustment> accountAdjustments =
                adjustments.findAllByDispute_Transaction_Card_IdOrderByAdjustmentDateAscIdAsc(cardId).stream()
                        .map(adjustment -> new AccountAdjustment(
                                adjustment.getDispute().getId(), adjustment.getKind(), adjustment.getAmount(),
                                adjustment.getAdjustmentDate()))
                        .toList();
        Set<Long> reversedDisputes = accountAdjustments.stream()
                .filter(adjustment -> adjustment.kind() == DisputeAdjustmentKind.REVERSAL)
                .map(AccountAdjustment::disputeId)
                .collect(Collectors.toSet());
        BigDecimal creditsInForce = accountAdjustments.stream()
                .filter(adjustment -> adjustment.kind() != DisputeAdjustmentKind.REVERSAL)
                .filter(adjustment -> !reversedDisputes.contains(adjustment.disputeId()))
                .map(AccountAdjustment::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new CardAccount(postedTotal, creditsInForce, postedTotal.subtract(creditsInForce),
                accountAdjustments);
    }

    public record CardAccount(
            BigDecimal postedTransactionsTotal,
            BigDecimal creditsInForceTotal,
            BigDecimal balanceOwed,
            List<AccountAdjustment> adjustments) {
    }

    public record AccountAdjustment(
            Long disputeId,
            DisputeAdjustmentKind kind,
            BigDecimal amount,
            java.time.LocalDate date) {
    }
}
