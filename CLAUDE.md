# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Requires Java 21.** The `JAVA_HOME` must point to a JDK 21 installation (Lombok annotation processing fails on Java 26).
```bash
# Ensure Java 21 is used (Homebrew example)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build and run tests
mvn clean verify

# Build without tests
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=PaymentMiddlewareServiceTest

# Run a single test method
mvn test -Dtest=PaymentMiddlewareServiceTest#shouldSucceedOnNormalResponse

# Run the application
mvn spring-boot:run
```

## Architecture

**paymently** is a Spring Boot 3.4 middleware that proxies bill inquiry/payment requests to an upstream payment API (`payment.uii.ac.id`). It runs on **port 8081** (Java 21).

### Auth flow (OAuth2 client_credentials)

Before calling any upstream endpoint, the service fetches an access token dynamically:
```
POST /v1.0/access-token/b2b
  Headers: X-CLIENT-KEY, X-SIGNATURE, X-TIMESTAMP, X-EXTERNAL-ID
  Body: {"grantType":"client_credentials"}
  → Response: {"accessToken":"...", "tokenType":"Bearer", "expiresIn":"3600"}
```
The token is **cached in memory** by `TokenService` until 60s before expiry, then re-fetched automatically.

### Request flow

```
POST /api/v1/bill/inquiry, /payment, /reverse  or  GET /api/v1/bill/healthz
  → BillController
    → PaymentMiddlewareService
      → TokenService.getAccessToken() (cached→upstream /v1.0/access-token/b2b)
      → RestTemplate (Apache HttpClient 5) → upstream /v2/bill/*
```

### Layers

- **`controller/BillController`** — Four endpoints:
  - `POST /api/v1/bill/inquiry` — proxies bill inquiry to upstream
  - `POST /api/v1/bill/payment` — proxies bill payment to upstream `/v2/bill/payment`
  - `POST /api/v1/bill/reverse` — proxies bill reversal to upstream `/v2/bill/reverse`
  - `GET /api/v1/bill/healthz` — proxies health check to upstream `/v2/bill/healthz`
- **`service/TokenService`** — Fetches OAuth2 access token from `/v1.0/access-token/b2b` using `X-CLIENT-KEY`, `X-SIGNATURE`, `X-TIMESTAMP`, `X-EXTERNAL-ID` headers. Caches token in-memory (`volatile` String + `Instant` expiry) with 60s expiry buffer. On `ResourceAccessException`, wraps in `RuntimeException` with message `"Gagal mendapatkan access token: ..."`.
- **`service/PaymentMiddlewareService`** — Calls `tokenService.getAccessToken()` to get a bearer token, then builds common auth headers (Bearer, CHANNEL-ID, X-EXTERNAL-ID, X-PARTNER-ID, X-SIGNATURE, X-TIMESTAMP) via `buildCommonHeaders()`. Handles `inquiryBill()` (POST), `paymentBill()` (POST), `reverseBill()` (POST), and `healthz()` (GET). On timeout/connection errors, wraps `ResourceAccessException` in a `RuntimeException` with `"Connect to <host>:443 failed: ..."`.
- **`config/RestTemplateConfig`** — Defines a `paymentRestTemplate` bean backed by Apache HttpClient 5 with a pooled connection manager (max 20 total, 10 per route). Configurable connect/read timeouts via `payment.api.connect-timeout` (default 10s) and `payment.api.read-timeout` (default 20s). Includes a request interceptor that logs headers (redacting `Authorization`) at DEBUG level. Note: `setReadTimeout(Duration)` is deprecated in the Spring Boot version used.
- **`exception/GlobalExceptionHandler`** — `@RestControllerAdvice`. Maps `ResourceAccessException` → 504 Gateway Timeout. Maps uncaught `RuntimeException` → 500. Both return an `ErrorResponse` body with timestamp, status, error, message, and path.
- **`dto/`** — Request/response POJOs using Lombok `@Builder` + Jackson annotations. `BillInquiryResponse` and `AccessTokenResponse` use `@JsonIgnoreProperties(ignoreUnknown = true)` to tolerate extra upstream fields.

### Configuration (`application.yml`)

- `payment.api.base-url` / `access-token-path` / `inquiry-path` / `payment-path` / `reverse-path` / `healthz-path` — upstream API endpoint parts.
- `payment.api.connect-timeout` / `read-timeout` — Duration values (e.g. `10s`).
- `payment.auth.client-key` — used as `X-CLIENT-KEY` header for token requests. Supports `${PAYMENT_CLIENT_KEY:default}`.
- `payment.auth.channel-id`, `partner-id`, `external-id`, `signature` — auth headers sent to upstream. `signature` supports `${PAYMENT_SIGNATURE:}` resolution.
- Logging is set to `DEBUG` for `com.uii.paymently`, `INFO` for `org.apache.http`.

## Testing

Tests are Spring Boot integration tests (not unit tests) — they start the full application context.

- **`RestTemplateConfigTest`** — Verifies the `paymentRestTemplate` bean is created and uses `HttpComponentsClientHttpRequestFactory` (Apache HttpClient 5), not the JDK default.
- **`PaymentMiddlewareServiceTest`** — Uses **WireMock** (standalone, dynamic port) to stub the upstream API. `@DynamicPropertySource` overrides `payment.api.base-url`, `access-token-path`, `inquiry-path`, and `healthz-path` to point at WireMock. A `@BeforeEach` sets up the access-token stub (`mock-access-token`, 3600s expiry). Four scenarios:
  - Normal inquiry response (`responseCode: "00"`)
  - Inquiry timeout (25s delay vs 20s read timeout)
  - Healthz success (`{"status":"UP"}`)
  - Token endpoint timeout (25s delay, expecting `"Gagal mendapatkan access token"`)
