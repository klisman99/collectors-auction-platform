package io.github.klisman99.collectorsauctionplatform.platform.web;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiProblems {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();

        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST,
                "REQUEST_VALIDATION_FAILED",
                null,
                "One or more request fields are invalid.",
                fieldErrors,
                request));
    }

    static ProblemDetail problem(
            HttpStatus status,
            String code,
            String ruleId,
            String detail,
            List<FieldViolation> fieldErrors,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:collectors-auction-platform:problem:" + code.toLowerCase()));
        problem.setProperty("code", code);
        problem.setProperty("ruleId", ruleId);
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("traceId", request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE));
        return problem;
    }

    private FieldViolation toFieldViolation(FieldError fieldError) {
        return new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }

    record FieldViolation(String field, String message) {
    }
}
