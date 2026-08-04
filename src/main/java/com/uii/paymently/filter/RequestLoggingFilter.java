package com.uii.paymently.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final RequestLogStore store;

    private static final int MAX_BODY_LENGTH = 4096;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {
        // Skip logging untuk endpoint internal (menghindari self-monitoring loop)
        if (request.getRequestURI().equals("/api/v1/bill/requests")) {
            try {
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                log.error("Request filter error: {}", e.getMessage());
            }
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception e) {
            log.error("Request filter error: {}", e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                store.add(buildEntry(requestWrapper, responseWrapper, duration));
            } catch (Exception e) {
                log.debug("Gagal menyimpan request log: {}", e.getMessage());
            }
            try {
                responseWrapper.copyBodyToResponse();
            } catch (Exception e) {
                log.debug("Gagal copy response body: {}", e.getMessage());
            }
        }
    }

    private RequestLogStore.RequestLogEntry buildEntry(
            ContentCachingRequestWrapper req,
            ContentCachingResponseWrapper res,
            long durationMs) {

        Map<String, String> reqHeaders = new LinkedHashMap<>();
        Collections.list(req.getHeaderNames())
                .forEach(name -> reqHeaders.put(name, req.getHeader(name)));

        byte[] reqBody = req.getContentAsByteArray();
        String reqBodyStr = reqBody.length > 0
                ? truncate(new String(reqBody, StandardCharsets.UTF_8))
                : "";

        Map<String, String> resHeaders = new LinkedHashMap<>();
        res.getHeaderNames()
                .forEach(name -> resHeaders.put(name, res.getHeader(name)));

        byte[] resBody = res.getContentAsByteArray();
        String resBodyStr = resBody.length > 0
                ? truncate(new String(resBody, StandardCharsets.UTF_8))
                : "";

        String uri = req.getRequestURI() +
                (req.getQueryString() != null ? "?" + req.getQueryString() : "");

        return RequestLogStore.RequestLogEntry.builder()
                .timestamp(Instant.now())
                .clientIp(getClientIp(req))
                .method(req.getMethod())
                .uri(uri)
                .upstreamUrl(mapUpstreamUrl(uri))
                .requestHeaders(reqHeaders)
                .requestBody(reqBodyStr)
                .responseStatus(res.getStatus())
                .responseHeaders(resHeaders)
                .responseBody(resBodyStr)
                .durationMs(durationMs)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private static final String UPSTREAM_BASE = "https://payment.uii.ac.id";

    private String mapUpstreamUrl(String uri) {
        if (uri.startsWith("/api/v1/bill/inquiry")) {
            return UPSTREAM_BASE + "/v2/bill/inquiry";
        }
        if (uri.startsWith("/api/v1/bill/healthz")) {
            return UPSTREAM_BASE + "/v2/bill/healthz";
        }
        return null;
    }

    private String truncate(String s) {
        return s.length() > MAX_BODY_LENGTH
                ? s.substring(0, MAX_BODY_LENGTH) + "...[truncated]"
                : s;
    }
}
