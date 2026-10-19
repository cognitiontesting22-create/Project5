package com.cardbench.disputes.domain;

import java.util.List;

public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    AWAITING_CARDMEMBER,
    RESOLVED_APPROVED,
    RESOLVED_DENIED,
    WITHDRAWN;

    public boolean canTransitionTo(DisputeStatus target) {
        return allowedTransitions().contains(target);
    }

    public List<DisputeStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> List.of(UNDER_REVIEW, WITHDRAWN);
            case UNDER_REVIEW -> List.of(
                    AWAITING_CARDMEMBER, RESOLVED_APPROVED, RESOLVED_DENIED, WITHDRAWN);
            case AWAITING_CARDMEMBER -> List.of(UNDER_REVIEW, WITHDRAWN);
            case RESOLVED_APPROVED, RESOLVED_DENIED, WITHDRAWN -> List.of();
        };
    }

    public boolean isResolvedOutcome() {
        return this == RESOLVED_APPROVED || this == RESOLVED_DENIED;
    }

    public String getDisplayName() {
        return switch (this) {
            case OPEN -> "Open";
            case UNDER_REVIEW -> "Under review";
            case AWAITING_CARDMEMBER -> "Awaiting cardmember";
            case RESOLVED_APPROVED -> "Resolved in cardmember's favor";
            case RESOLVED_DENIED -> "Resolved in merchant's favor";
            case WITHDRAWN -> "Withdrawn";
        };
    }
}
