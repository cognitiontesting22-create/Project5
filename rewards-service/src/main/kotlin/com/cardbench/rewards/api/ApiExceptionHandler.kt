package com.cardbench.rewards.api

import com.cardbench.rewards.service.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.cardbench.rewards.api"])
class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(exception: ResourceNotFoundException) =
        ErrorResponse("NOT_FOUND", exception.message ?: "Resource not found")

    data class ErrorResponse(val code: String, val message: String)
}
