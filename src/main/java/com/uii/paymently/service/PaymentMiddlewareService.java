package com.uii.paymently.service;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PaymentMiddlewareService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String inquiryPath;
    private final String bearerToken;
    private final String channelId;
    private final String partnerId;
    private final String externalId;
    private final String signature;

    public PaymentMiddlewareService(
            @Qualifier("paymentRestTemplate") RestTemplate restTemplate,
            @Value("${payment.api.base-url}") String baseUrl,
            @Value("${payment.api.inquiry-path}") String inquiryPath,
            @Value("${payment.auth.bearer-token}") String bearerToken,
            @Value("${payment.auth.channel-id}") String channelId,
            @Value("${payment.auth.partner-id}") String partnerId,
            @Value("${payment.auth.external-id}") String externalId,
            @Value("${payment.auth.signature}") String signature) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.inquiryPath = inquiryPath;
        this.bearerToken = bearerToken;
        this.channelId = channelId;
        this.partnerId = partnerId;
        this.externalId = externalId;
        this.signature = signature;
    }

    /**
     * Hit bill inquiry API.
     * Saat timeout/connection error, log detail dan throw runtime exception.
     */
    public BillInquiryResponse inquiryBill(BillInquiryRequest request) {
        String url = baseUrl + inquiryPath;
        String timestamp = ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        headers.set("CHANNEL-ID", channelId);
        headers.set("X-EXTERNAL-ID", externalId);
        headers.set("X-PARTNER-ID", partnerId);
        headers.set("X-SIGNATURE", signature);
        headers.set("X-TIMESTAMP", timestamp);

        HttpEntity<BillInquiryRequest> entity = new HttpEntity<>(request, headers);

        log.debug("=== HEADER ===");
        headers.forEach((key, value) -> log.debug("{}: {}",
                key, key.equalsIgnoreCase("Authorization") ? "Bearer ****" : value));
        log.debug("=== URL {} ===", url);

        try {
            ResponseEntity<BillInquiryResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, BillInquiryResponse.class);
            log.info("Inquiry success: code={}, message={}",
                    response.getBody() != null ? response.getBody().getResponseCode() : "null",
                    response.getBody() != null ? response.getBody().getResponseMessage() : "null");
            return response.getBody();
        } catch (ResourceAccessException e) {
            // Ekstrak root cause untuk logging yang clean
            Throwable rootCause = e.getRootCause() != null ? e.getRootCause() : e;
            String errorDetail = buildTimeoutErrorMessage(url, rootCause);
            // Log dalam format yang mirip dengan output yang user inginkan
            log.error(errorDetail);
            // Log full stacktrace secara terpisah (lebih verbose)
            log.debug("Full stacktrace", e);
            throw new RuntimeException(errorDetail, e);
        }
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
        // Dari "https://payment.uii.ac.id/v2/bill/inquiry" → "payment.uii.ac.id"
        return url.replaceFirst("^https?://", "")
                  .replaceFirst("/.*$", "");
    }
}