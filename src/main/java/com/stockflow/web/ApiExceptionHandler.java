package com.stockflow.web;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.common.ConflictException;
import com.stockflow.common.DomainException;
import com.stockflow.common.NotFoundException;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into the same JSON shape (RFC 9457 problem details), for example:
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Insufficient stock",
 *   "status": 409,
 *   "detail": "Store SV-001 has 4 unit(s) of MUG-001 but 30 were requested",
 *   "instance": "/api/v1/transfers/7/dispatch",
 *   "sku": "MUG-001",
 *   "available": 4
 * }
 * </pre>
 * Extending {@link ResponseEntityExceptionHandler} gives the same format to Spring's own errors
 * (malformed JSON, wrong parameter types, unsupported methods and so on).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return toProblem(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return toProblem(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRule(BusinessRuleViolationException ex) {
        return toProblem(HttpStatus.UNPROCESSABLE_CONTENT, ex);
    }

    /** Optimistic locking rejected a write because another request changed the same row first. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail handleConcurrentModification(ConcurrencyFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The data was changed by another request at the same time. Please retry.");
        problem.setTitle("Concurrent modification");
        return problem;
    }

    /** A database constraint fired (for example two requests creating the same SKU at once). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with data that already exists.");
        problem.setTitle("Data integrity violation");
        return problem;
    }

    /** Bean Validation failures: list every invalid field instead of a generic message. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail toProblem(HttpStatus status, DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.getTitle());
        ex.getDetails().forEach(problem::setProperty);
        return problem;
    }

    public record FieldViolation(String field, String message) {
    }
}
