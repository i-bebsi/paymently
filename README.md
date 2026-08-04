# Paymently

**Payment API Middleware Connector for UII** — Spring Boot middleware yang menjembatani aplikasi internal dengan Payment API Gateway (`payment.uii.ac.id`).

## Quick Start (Docker)

```bash
# 1. Siapkan kredensial
cp .env.example .env
# Edit .env — isi semua PAYMENT_* variable

# 2. Jalankan
docker compose -f docker-compose.paymently.yml up -d --build

# 3. Cek
curl http://localhost:8081/api/v1/bill/live
open http://localhost:9090     # dashboard
```

## Quick Start (Local)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PAYMENT_CLIENT_KEY="..." PAYMENT_SIGNATURE="..." # + semua env vars lain
mvn spring-boot:run
```

## API Endpoints

| Method | Endpoint | Keterangan |
|---|---|---|
| `GET` | `/api/v1/bill/live` | Internal liveness — `{"status":"UP","service":"paymently"}` |
| `GET` | `/api/v1/bill/healthz` | Upstream health proxy → `payment.uii.ac.id/v2/bill/healthz` |
| `POST` | `/api/v1/bill/inquiry` | Bill inquiry proxy → `payment.uii.ac.id/v2/bill/inquiry` |
| `GET` | `/api/v1/bill/requests` | Request log (untuk dashboard) |

### Header Override (`/healthz`)

| Header | Fungsi |
|---|---|
| `X-CLIENT-KEY` | Override `CHANNEL-ID` + `X-CLIENT-KEY` (token) |
| `X-TIMESTAMP` | Override `X-TIMESTAMP` |
| `X-EXTERNAL-ID` | Override `X-EXTERNAL-ID` |

```bash
curl http://localhost:8081/api/v1/bill/healthz \
  -H 'X-CLIENT-KEY: KPBW' \
  -H 'X-TIMESTAMP: 2026-08-01T10:30:00+07:00' \
  -H 'X-EXTERNAL-ID: KPBW'
```

### Inquiry

```bash
curl -X POST http://localhost:8081/api/v1/bill/inquiry \
  -H 'Content-Type: application/json' \
  -d '{"customerNo":"0226016324"}'
```

## Konfigurasi

Semua property di `application.yml` bisa di-override via env var (Spring Boot relaxed binding):

| Env Variable | Default | Keterangan |
|---|---|---|
| `PAYMENT_API_BASE_URL` | — | Upstream base URL |
| `PAYMENT_API_ACCESS_TOKEN_PATH` | — | OAuth2 token path |
| `PAYMENT_API_INQUIRY_PATH` | — | Inquiry path |
| `PAYMENT_API_HEALTHZ_PATH` | `/v2/bill/healthz` | Health check path |
| `PAYMENT_API_CONNECT_TIMEOUT` | `10s` | TCP connect timeout |
| `PAYMENT_API_READ_TIMEOUT` | `20s` | Read timeout |
| `PAYMENT_CLIENT_KEY` | — | X-CLIENT-KEY (token request) |
| `PAYMENT_CHANNEL_ID` | — | CHANNEL-ID |
| `PAYMENT_PARTNER_ID` | — | X-PARTNER-ID |
| `PAYMENT_EXTERNAL_ID` | — | X-EXTERNAL-ID |
| `PAYMENT_SIGNATURE` | — | X-SIGNATURE |
| `PAYMENT_AUTH_TIMESTAMP` | — | X-TIMESTAMP |

## Autentikasi

OAuth2 Client Credentials → token di-cache in-memory + buffer 60s. Header override → bypass cache, fetch token baru.

## Dashboard

Dashboard real-time di **http://localhost:9090** (auto-refresh 30s):

- KPI tiles: Total Requests, Success (2xx), Failure (4xx/5xx)
- Failures table + Request Log — pagination (10/50/100), search, klik row → detail
- Export PDF (⎙ PDF button → browser print)
- Dark mode toggle

```bash
# Manual (tanpa Docker)
python3 monitor-server.py &
open http://localhost:9090
```

## Monitoring

- **Uptime Kuma**: project terpisah di `../uptime_kuma/` — `docker compose up -d`
- **Manual check**: `./healthz-check.sh` → append ke `logs/healthz-monitor.log`

## Pengujian

```bash
mvn test
mvn test -Dtest=PaymentMiddlewareServiceTest
mvn clean package -DskipTests
```

WireMock integration test — 4 skenario: inquiry sukses, inquiry timeout, healthz sukses, token timeout.

## Struktur Proyek

```
src/main/java/com/uii/paymently/
├── controller/BillController.java    # 4 endpoint
├── config/RestTemplateConfig.java    # Apache HttpClient 5
├── filter/
│   ├── RequestLogStore.java          # In-memory + file persistence
│   └── RequestLoggingFilter.java     # Tangkap request/response
├── service/
│   ├── PaymentMiddlewareService.java # Proxy bill inquiry + health
│   └── TokenService.java             # OAuth2 token + cache
├── dto/                              # Request/response POJOs
└── exception/GlobalExceptionHandler.java

Dockerfile docker-compose.paymently.yml .env.example dashboard.html ...
```

## Teknologi

Java 21 · Spring Boot 3.4.1 · Apache HttpClient 5 · WireMock 3.9 · Logstash Logback 8.0 · Lombok · Maven
