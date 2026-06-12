package com.example.knowledgeassistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        Instant startedAt = Instant.now();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            log.info(
                    "HTTP request started method={} path={} query={} remote={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    safeQuery(request.getQueryString()),
                    request.getRemoteAddr()
            );
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info(
                    "HTTP request completed method={} path={} status={} elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs
            );
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String safeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "-";
        }
        return queryString.replaceAll("(?i)(token|key|password|secret)=[^&]*", "$1=***");
    }
}
