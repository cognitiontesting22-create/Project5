package com.cardbench.disputes.domain;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "dispute_events")
public class DisputeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false, updatable = false)
    private Dispute dispute;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DisputeEventKind kind;

    @Column(nullable = false, length = 100, updatable = false)
    private String actor;

    @Column(nullable = false, length = 1100, updatable = false)
    private String text;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    protected DisputeEvent() {
    }

    public DisputeEvent(Dispute dispute, Instant occurredAt, DisputeEventKind kind,
                        String actor, String text, boolean internal) {
        this.dispute = dispute;
        this.occurredAt = occurredAt;
        this.kind = kind;
        this.actor = actor;
        this.text = text;
        this.internal = internal;
    }

    public Long getId() {
        return id;
    }

    public Dispute getDispute() {
        return dispute;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public DisputeEventKind getKind() {
        return kind;
    }

    public String getActor() {
        return actor;
    }

    public String getText() {
        return text;
    }

    public boolean isInternal() {
        return internal;
    }

    @PreUpdate
    @PreRemove
    private void rejectMutation() {
        throw new IllegalStateException("Dispute history events are immutable");
    }
}
