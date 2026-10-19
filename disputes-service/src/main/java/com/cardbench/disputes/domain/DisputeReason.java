package com.cardbench.disputes.domain;

public enum DisputeReason {
    FRAUD("Fraud"),
    DUPLICATE_CHARGE("Duplicate charge"),
    NOT_RECEIVED("Item or service not received"),
    NOT_AS_DESCRIBED("Item or service not as described"),
    CANCELLED_SUBSCRIPTION("Cancelled subscription"),
    INCORRECT_AMOUNT("Incorrect amount");

    private final String displayName;

    DisputeReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
