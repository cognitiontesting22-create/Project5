package com.cardbench.disputes.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.cardbench.disputes.domain.DisputeStatus;

public final class DisputeRequests {
    private DisputeRequests() {
    }

    public record FileDisputeRequest(
            @NotBlank String transactionId,
            BigDecimal amount,
            String reasonCode,
            String description) {
    }

    public record SetStatusRequest(
            @NotNull DisputeStatus status,
            @Size(max = 500) String resolutionNote) {
    }
}
