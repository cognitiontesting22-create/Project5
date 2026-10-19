package com.cardbench.disputes.domain;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "disputes")
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private CardTransaction transaction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeReason reasonCode;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @Column(length = 500)
    private String resolutionNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeCreditState creditState = DisputeCreditState.NONE;

    protected Dispute() {
    }

    public Dispute(CardTransaction transaction, BigDecimal amount, DisputeReason reasonCode, String description) {
        this.transaction = transaction;
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.description = description;
        this.status = DisputeStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public CardTransaction getTransaction() {
        return transaction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DisputeReason getReasonCode() {
        return reasonCode;
    }

    public String getDescription() {
        return description;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public DisputeCreditState getCreditState() {
        return creditState;
    }

    public void transitionTo(DisputeStatus status, String resolutionNote) {
        this.status = status;
        this.resolutionNote = resolutionNote;
    }

    public void setCreditState(DisputeCreditState creditState) {
        this.creditState = creditState;
    }
}
