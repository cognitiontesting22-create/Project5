package com.cardbench.disputes.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidResolutionNoteException extends RuntimeException {
    public InvalidResolutionNoteException(String message) {
        super(message);
    }
}
