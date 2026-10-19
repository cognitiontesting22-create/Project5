package com.cardbench.disputes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_cards")
public class PaymentCard {
    @Id
    private String id;
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cardmember_id", nullable = false)
    private Cardmember cardmember;

    protected PaymentCard() {
    }

    public PaymentCard(String id, String label, Cardmember cardmember) {
        this.id = id;
        this.label = label;
        this.cardmember = cardmember;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Cardmember getCardmember() {
        return cardmember;
    }
}
