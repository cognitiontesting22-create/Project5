package com.cardbench.disputes.service;

public class InvalidQueueRequestException extends RuntimeException {
    public InvalidQueueRequestException(String message) {
        super(message);
    }
}
