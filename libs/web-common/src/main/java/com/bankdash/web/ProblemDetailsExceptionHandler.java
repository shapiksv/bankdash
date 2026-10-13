package com.bankdash.web;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Spring MVC's standard exceptions as ProblemDetail, with field errors in the shared {@code errors} shape. It
 * runs last, so a service's own {@code @RestControllerAdvice} for its domain exceptions takes precedence.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Problems.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new Problems.FieldError(error.getField(), String.valueOf(error.getDefaultMessage())))
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        return handleExceptionInternal(ex, Problems.validation(errors), headers, status, request);
    }
}
