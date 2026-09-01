package io.github.klisman99.collectorsauctionplatform.platform.web;

import java.io.IOException;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RequestTraceFilter extends OncePerRequestFilter {

    static final String TRACE_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    RequestTraceFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String traceId = currentTraceId(request);

        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String currentTraceId(HttpServletRequest request) {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceId();
        }

        String requestedTraceId = request.getHeader(TRACE_ID_HEADER);
        return requestedTraceId == null || requestedTraceId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedTraceId;
    }
}
