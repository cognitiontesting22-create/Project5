package com.cardbench.disputes.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_transactions")
public class CardTransaction {
    @Id
    private String id;
    private String merchant;
    private String category;
    private BigDecimal amount;
    private LocalDate postingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private PaymentCard card;

    protected CardTransaction() {
    }

    public CardTransaction(String id, String merchant, String category, BigDecimal amount,
                           LocalDate postingDate, PaymentCard card) {
        this.id = id;
        this.merchant = merchant;
        this.category = category;
        this.amount = amount;
        this.postingDate = postingDate;
        this.card = card;
    }

    public String getId() {
        return id;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public PaymentCard getCard() {
        return card;
    }
}
