package com.cardbench.disputes.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "dispute_adjustments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dispute_id", "kind"}))
public class DisputeAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeAdjustmentKind kind;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate adjustmentDate;

    protected DisputeAdjustment() {
    }

    public DisputeAdjustment(Dispute dispute, DisputeAdjustmentKind kind, BigDecimal amount,
                             LocalDate adjustmentDate) {
        this.dispute = dispute;
        this.kind = kind;
        this.amount = amount;
        this.adjustmentDate = adjustmentDate;
    }

    public Long getId() {
        return id;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public DisputeAdjustmentKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getAdjustmentDate() {
        return adjustmentDate;
    }

    public void makePermanent() {
        this.kind = DisputeAdjustmentKind.PERMANENT_CREDIT;
    }
}
