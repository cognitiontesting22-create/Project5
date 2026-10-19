package com.cardbench.disputes.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cardbench.disputes.service.ResourceNotFoundException;
import com.cardbench.disputes.service.InvalidDisputeTransitionException;
import com.cardbench.disputes.service.InvalidResolutionNoteException;
import com.cardbench.disputes.service.DisputeFilingException;

@RestControllerAdvice(basePackages = "com.cardbench.disputes.api")
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ResourceNotFoundException exception) {
        return new ErrorResponse("NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidDisputeTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse invalidTransition(InvalidDisputeTransitionException exception) {
        return new ErrorResponse("INVALID_TRANSITION", exception.getMessage());
    }

    @ExceptionHandler(InvalidResolutionNoteException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidResolutionNote(InvalidResolutionNoteException exception) {
        return new ErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(DisputeFilingException.class)
    public ResponseEntity<ErrorResponse> disputeFilingRejected(DisputeFilingException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(Exception exception) {
        return new ErrorResponse("BAD_REQUEST", "Request body is invalid or contains an unsupported value");
    }

    public record ErrorResponse(String code, String message) {
    }
}
