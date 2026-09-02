package io.github.klisman99.collectorsauctionplatform.platform.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiProblems {

    private static final Logger logger = LoggerFactory.getLogger(ApiProblems.class);

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_BODY_UNREADABLE",
                "The request body is missing or malformed.",
                request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> invalidRequestParameter(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_PARAMETER_INVALID",
                "One or more request parameters are invalid.",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> resourceNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The requested resource does not exist.",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "The request method is not supported for this resource.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpectedFailure(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled API failure", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed.",
                request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(problem(status, code, null, detail, List.of(), request));
    }

    static ProblemDetail problem(
            HttpStatus status,
            String code,
            String ruleId,
            String detail,
            List<FieldViolation> fieldErrors,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:collectors-auction-platform:problem:" + code.toLowerCase(Locale.ROOT)));
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
