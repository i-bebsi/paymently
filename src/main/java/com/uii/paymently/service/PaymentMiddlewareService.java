package com.uii.paymently.service;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

@Slf4j
@Service
public class PaymentMiddlewareService {

    private final RestTemplate restTemplate;
    private final TokenService tokenService;
    private final String baseUrl;
    private final String inquiryPath;
    private final String healthzPath;
    private final String channelId;
    private final String partnerId;
    private final String externalId;
    private final String signature;
    private final String timestamp;

    public PaymentMiddlewareService(
            @Qualifier("paymentRestTemplate") RestTemplate restTemplate,
            TokenService tokenService,
            @Value("${payment.api.base-url}") String baseUrl,
            @Value("${payment.api.inquiry-path}") String inquiryPath,
            @Value("${payment.api.healthz-path:/v2/bill/healthz}") String healthzPath,
            @Value("${payment.auth.channel-id}") String channelId,
            @Value("${payment.auth.partner-id}") String partnerId,
            @Value("${payment.auth.external-id}") String externalId,
            @Value("${payment.auth.signature}") String signature,
            @Value("${payment.auth.timestamp}") String timestamp) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.baseUrl = baseUrl;
        this.inquiryPath = inquiryPath;
        this.healthzPath = healthzPath;
        this.channelId = channelId;
        this.partnerId = partnerId;
        this.externalId = externalId;
        this.signature = signature;
        this.timestamp = timestamp;
    }

    /**
     * Hit bill inquiry API.
     * Saat timeout/connection error, log detail dan throw runtime exception.
     */
    public BillInquiryResponse inquiryBill(BillInquiryRequest request) {
        String url = baseUrl + inquiryPath;

        try {
            HttpHeaders headers = buildCommonHeaders();
            HttpEntity<BillInquiryRequest> entity = new HttpEntity<>(request, headers);

            log.debug("=== HEADER ===");
            headers.forEach((key, value) -> log.debug("{}: {}",
                    key, key.equalsIgnoreCase("Authorization") ? "Bearer ****" : value));
            log.debug("=== URL {} ===", url);

            ResponseEntity<BillInquiryResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, BillInquiryResponse.class);
            log.info("Inquiry success: code={}, message={}",
                    response.getBody() != null ? response.getBody().getResponseCode() : "null",
                    response.getBody() != null ? response.getBody().getResponseMessage() : "null");
            return response.getBody();
        } catch (ResourceAccessException e) {
            Throwable rootCause = e.getRootCause() != null ? e.getRootCause() : e;
            String errorDetail = buildTimeoutErrorMessage(url, rootCause);
            log.error(errorDetail);
            log.debug("Full stacktrace", e);
            throw e; // propagate asli agar GlobalExceptionHandler → 504
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Upstream inquiry error: HTTP {} — {}", e.getStatusCode().value(), responseBody);
            throw new ResourceAccessException(
                    String.format("Upstream inquiry returned HTTP %s: %s",
                            e.getStatusCode().value(), responseBody));
        }
    }

    /**
     * Hit healthz API upstream (pakai semua nilai default dari config).
     */
    public Map<String, Object> healthz() {
        return healthz(null, null, null);
    }

    /**
     * Hit healthz API upstream dengan header override.
     * GET /v2/bill/healthz
     * @param overrideClientKey  jika tidak null, override X-CLIENT-KEY & CHANNEL-ID
     * @param overrideTimestamp  jika tidak null, override X-TIMESTAMP
     * @param overrideExternalId jika tidak null, override X-EXTERNAL-ID
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> healthz(String overrideClientKey,
                                       String overrideTimestamp,
                                       String overrideExternalId) {
        String url = baseUrl + healthzPath;

        try {
            HttpHeaders headers = buildCommonHeaders(overrideClientKey,
                    overrideTimestamp, overrideExternalId);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            log.debug("=== HEADER ===");
            headers.forEach((key, value) -> log.debug("{}: {}",
                    key, key.equalsIgnoreCase("Authorization") ? "Bearer ****" : value));
            log.debug("=== URL {} ===", url);

            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            log.info("Healthz success: {}", response.getBody());
            return response.getBody();
        } catch (ResourceAccessException e) {
            Throwable rootCause = e.getRootCause() != null ? e.getRootCause() : e;
            String errorDetail = buildTimeoutErrorMessage(url, rootCause);
            log.error(errorDetail);
            log.debug("Full stacktrace", e);
            throw e; // propagate asli agar GlobalExceptionHandler → 504
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Upstream healthz error: HTTP {} — {}", e.getStatusCode().value(), responseBody);
            throw new ResourceAccessException(
                    String.format("Upstream healthz returned HTTP %s: %s",
                            e.getStatusCode().value(), responseBody));
        }
    }

    private HttpHeaders buildCommonHeaders() {
        return buildCommonHeaders(null, null, null);
    }

    /**
     * Bangun header umum yang dipakai inquiry maupun healthz.
     * @param overrideClientKey  jika tidak null, override nilai default dari config
     * @param overrideTimestamp  jika tidak null, override nilai default dari config
     * @param overrideExternalId jika tidak null, override nilai default dari config
     */
    private HttpHeaders buildCommonHeaders(String overrideClientKey,
                                           String overrideTimestamp,
                                           String overrideExternalId) {
        // Token request juga perlu override yang sama
        String accessToken = tokenService.getAccessToken(
                overrideClientKey, overrideTimestamp, overrideExternalId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("CHANNEL-ID", overrideClientKey != null ? overrideClientKey : channelId);
        headers.set("X-EXTERNAL-ID", overrideExternalId != null ? overrideExternalId : externalId);
        headers.set("X-PARTNER-ID", partnerId);
        headers.set("X-SIGNATURE", signature);
        headers.set("X-TIMESTAMP", overrideTimestamp != null ? overrideTimestamp : timestamp);
        return headers;
    }

    /**
     * Bangun pesan error yang mirip format:
     * "Connect to payment.uii.ac.id:443 [payment.uii.ac.id/IP] failed: Operation timed out (Connection timed out)"
     */
    private String buildTimeoutErrorMessage(String url, Throwable rootCause) {
        String host = extractHost(url);
        String message = rootCause.getMessage() != null ? rootCause.getMessage() : "Connection timed out";

        if (rootCause instanceof SocketTimeoutException) {
            return String.format("Connect to %s:443 failed: Read timed out — %s", host, message);
        } else if (rootCause instanceof ConnectException) {
            return String.format("Connect to %s:443 failed: %s", host, message);
        }
        return String.format("Connect to %s:443 failed: %s — %s", host,
                rootCause.getClass().getSimpleName(), message);
    }

    private String extractHost(String url) {
        return url.replaceFirst("^https?://", "")
                  .replaceFirst("/.*$", "");
    }
}
