package com.uii.paymently.service;

import com.uii.paymently.dto.AccessTokenRequest;
import com.uii.paymently.dto.AccessTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Slf4j
@Service
public class TokenService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String accessTokenPath;
    private final String clientKey;
    private final String signature;
    private final String externalId;
    private final String timestamp;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    /**
     * Reset token cache (untuk testing).
     */
    void clearToken() {
        cachedToken = null;
        tokenExpiry = Instant.EPOCH;
    }

    public TokenService(
            @Qualifier("paymentRestTemplate") RestTemplate restTemplate,
            @Value("${payment.api.base-url}") String baseUrl,
            @Value("${payment.api.access-token-path}") String accessTokenPath,
            @Value("${payment.auth.client-key}") String clientKey,
            @Value("${payment.auth.signature}") String signature,
            @Value("${payment.auth.external-id}") String externalId,
            @Value("${payment.auth.timestamp}") String timestamp) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.accessTokenPath = accessTokenPath;
        this.clientKey = clientKey;
        this.signature = signature;
        this.externalId = externalId;
        this.timestamp = timestamp;
    }

    /**
     * Mengembalikan access token yang valid (pakai nilai default dari config).
     */
    public String getAccessToken() {
        return getAccessToken(null, null, null);
    }

    /**
     * Mengembalikan access token yang valid.
     * Jika ada override, bypass cache dan fetch token baru.
     * @param overrideClientKey  jika tidak null, override X-CLIENT-KEY
     * @param overrideTimestamp  jika tidak null, override X-TIMESTAMP
     * @param overrideExternalId jika tidak null, override X-EXTERNAL-ID
     */
    public String getAccessToken(String overrideClientKey,
                                 String overrideTimestamp,
                                 String overrideExternalId) {
        boolean hasOverride = overrideClientKey != null
                || overrideTimestamp != null
                || overrideExternalId != null;

        // Tanpa override: gunakan cache
        if (!hasOverride && cachedToken != null
                && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            log.debug("Menggunakan cached access token (expires at {})", tokenExpiry);
            return cachedToken;
        }

        if (hasOverride) {
            log.info("Meminta access token baru dengan header override (bypass cache)");
        } else {
            log.info("Meminta access token baru dari {}", accessTokenPath);
        }
        String url = baseUrl + accessTokenPath;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CLIENT-KEY", overrideClientKey != null ? overrideClientKey : clientKey);
        headers.set("X-SIGNATURE", signature);
        headers.set("X-TIMESTAMP", overrideTimestamp != null ? overrideTimestamp : timestamp);
        headers.set("X-EXTERNAL-ID", overrideExternalId != null ? overrideExternalId : externalId);

        log.debug("=== TOKEN REQUEST HEADERS ===");
        headers.forEach((key, value) -> log.debug("{}: {}", key, value));
        log.debug("=== URL {} ===", url);

        AccessTokenRequest body = AccessTokenRequest.builder()
                .grantType("client_credentials")
                .build();

        HttpEntity<AccessTokenRequest> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<AccessTokenResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, AccessTokenResponse.class);

            AccessTokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new RuntimeException("Access token response body kosong atau tidak mengandung accessToken");
            }

            cachedToken = tokenResponse.getAccessToken();

            // Hitung expiry dari expiresIn (dalam detik)
            long expiresInSeconds = Long.parseLong(tokenResponse.getExpiresIn());
            tokenExpiry = Instant.now().plusSeconds(expiresInSeconds);

            log.info("Access token berhasil diperoleh, expires in {}s (at {})", expiresInSeconds, tokenExpiry);
            return cachedToken;

        } catch (ResourceAccessException e) {
            Throwable rootCause = e.getRootCause() != null ? e.getRootCause() : e;
            log.error("Gagal mendapatkan access token (koneksi/timeout): {}", rootCause.getMessage());
            log.debug("Full stacktrace", e);
            throw e; // propagate asli agar GlobalExceptionHandler mapping 504 tetap jalan
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Gagal mendapatkan access token: HTTP {} — {}",
                    e.getStatusCode().value(), responseBody);
            throw new ResourceAccessException(
                    String.format("Token endpoint returned HTTP %s: %s",
                            e.getStatusCode().value(), responseBody));
        }
    }
}
