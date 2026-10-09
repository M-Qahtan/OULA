package com.oula.api;

import com.oula.compliance.PolicyDecision;
import com.oula.compliance.PolicyEnforcementException;
import com.oula.platform.idempotency.IdempotencyConflictException;
import com.oula.platform.idempotency.IdempotencyInProgressException;
import com.oula.transaction.OptimisticConcurrencyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.NoSuchElementException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(PolicyEnforcementException.class)
    ResponseEntity<ProblemDetail> policyEnforcement(PolicyEnforcementException ex) {
        PolicyDecision decision = ex.policyDecision();
        HttpStatus status = decision.decision() == PolicyDecision.Decision.DENY
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CONFLICT;
        ProblemDetail detail = detail(
                status,
                "Policy precondition not satisfied",
                ex.getMessage(),
                "policy-precondition"
        );
        detail.setProperty("decision", decision.decision().name());
        detail.setProperty("reasonCodes", decision.reasonCodes());
        detail.setProperty("policyDecisionId", decision.id());
        if (decision.policyRuleId() != null) {
            detail.setProperty("policyRuleId", decision.policyRuleId());
        }
        return ResponseEntity.status(status).body(detail);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "resource-not-found");
    }

    @ExceptionHandler({
            OptimisticConcurrencyException.class,
            IdempotencyConflictException.class,
            IllegalStateException.class,
            DataIntegrityViolationException.class
    })
    ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Request conflict", ex.getMessage(), "request-conflict");
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    ResponseEntity<ProblemDetail> inProgress(IdempotencyInProgressException ex) {
        ProblemDetail detail = detail(
                HttpStatus.CONFLICT,
                "Operation already in progress",
                ex.getMessage(),
                "idempotency-in-progress"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "2")
                .body(detail);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    ResponseEntity<ProblemDetail> forbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "invalid-request");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String description,
            String type
    ) {
        return ResponseEntity.status(status).body(detail(status, title, description, type));
    }

    private ProblemDetail detail(
            HttpStatus status,
            String title,
            String description,
            String type
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, description);
        detail.setTitle(title);
        detail.setType(URI.create("urn:oula:problem:" + type));
        return detail;
    }
}
