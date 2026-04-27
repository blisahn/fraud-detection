package com.devblo.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Instant start = Instant.now();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.info(" -> {} {} from {}", method, uri, request.getRemoteAddr());

        filterChain.doFilter(request, response);

        long duration = Duration.between(start, Instant.now()).toMillis();
        log.info(" -> {} {} -> {} ({}ms)", method, uri, response.getStatus(), duration);

    }
}
