package com.cardbench.disputes.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidDisputeTransitionException extends RuntimeException {
    public InvalidDisputeTransitionException(String message) {
        super(message);
    }
}
