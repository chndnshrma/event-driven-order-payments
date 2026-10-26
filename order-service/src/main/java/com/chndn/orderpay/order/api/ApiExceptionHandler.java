package com.chndn.orderpay.order.api;

import com.chndn.orderpay.order.service.IdempotencyConflictException;
import com.chndn.orderpay.order.service.OrderNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail notFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** Violations on @RequestHeader / @PathVariable parameters (body errors are handled by Spring itself). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidParameter(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
