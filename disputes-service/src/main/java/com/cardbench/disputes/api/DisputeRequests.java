package com.cardbench.disputes.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.cardbench.disputes.domain.DisputeStatus;

public final class DisputeRequests {
    private DisputeRequests() {
    }

    public record FileDisputeRequest(
            @NotBlank String transactionId,
            @NotNull BigDecimal amount,
            @NotNull String reason) {
    }

    public record SetStatusRequest(@NotNull DisputeStatus status) {
    }
}
