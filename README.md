# Paymently

**Payment API Middleware Connector for UII**

Paymently adalah _middleware_ Spring Boot yang menjembatani aplikasi internal UII dengan Payment API Gateway eksternal (`payment.uii.ac.id`). Layanan ini menangani autentikasi OAuth2 secara otomatis, meneruskan (_proxy_) permintaan _bill inquiry_ dan _health check_, serta memberikan penanganan _error_ yang terstruktur.

## Arsitektur

```
Aplikasi Internal
      │
      ▼
┌─────────────────────────────────────────────┐
│  BillController  (port 8081)                │
│  GET  /api/v1/bill/healthz   ← liveness    │
│  GET  /api/v1/bill/health    ← proxy       │
│  POST /api/v1/bill/inquiry   ← proxy       │
│  GET  /api/v1/bill/requests  ← request log │
└────────────┬────────────────────────────────┘
             │
      ┌──────┴──────┐
      ▼              ▼
┌───────────┐  ┌──────────────────┐
│TokenService│  │PaymentMiddleware │
│ (OAuth2)   │  │Service (proxy)   │
│ cache      │  │                  │
└─────┬─────┘  └────────┬─────────┘
      │                 │
      ▼                 ▼
┌─────────────────────────────────┐
│     RestTemplate                │
│  (Apache HttpClient 5, pool)    │
└───────────────┬─────────────────┘
                │
                ▼
     payment.uii.ac.id
     ├── /v1.0/access-token/b2b/   (OAuth2 token)
     ├── /v2/bill/inquiry           (bill inquiry)
     └── /v2/bill/healthz           (health check)
```

### Dependency Graph

```
BillController
  └── PaymentMiddlewareService
        ├── RestTemplate (paymentRestTemplate)
        └── TokenService
              └── RestTemplate (same bean)

GlobalExceptionHandler → ErrorResponse (DTO)
```

## Fitur

- **OAuth2 Client Credentials** — Mengambil _access token_ secara dinamis dari `/v1.0/access-token/b2b` dan menyimpannya di _cache_ hingga mendekati masa kedaluwarsa.
- **Bill Inquiry** — Meneruskan permintaan pemeriksaan tagihan ke upstream `/v2/bill/inquiry`.
- **Health Check** — Dua endpoint: internal liveness (`/healthz`) dan proxy ke upstream (`/health`).
- **Header Override** — Endpoint `/health` mendukung header custom dari client untuk meng-override nilai default konfigurasi.
- **Request Logging** — Setiap request dicatat (IP, method, URI, headers, body, response, duration) via `RequestLoggingFilter`, disimpan di in-memory ring buffer (200 entry).
- **Dashboard Real-time** — Dashboard HTML di `http://localhost:9090` dengan KPI tiles, failures table, request log + pagination + PDF export.
- **Timeout Handling** — Koneksi _timeout_ upstream dikembalikan sebagai HTTP 504 Gateway Timeout dengan pesan yang jelas.
- **Structured Logging** — Log dalam format JSON (Logstash) ke konsol dan file (`logs/paymently.json`).

## Prasyarat

- **Java 21** — Lombok _annotation processor_ tidak kompatibel dengan Java 26+. Pastikan `JAVA_HOME` mengarah ke JDK 21:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # macOS Homebrew
  ```
- **Maven 3.9+**

## Konfigurasi

Konfigurasi dilakukan melalui `application.yml` di `src/main/resources/`. Semua property menggunakan `@Value` dan dapat di-_override_ via _environment variable_ (Spring Boot relaxed binding — titik jadi underscore uppercase, misal `payment.api.base-url` → `PAYMENT_API_BASE_URL`).

### `payment.api.*` — Upstream API

| Property | Env Variable | Default | Deskripsi |
|---|---|---|---|
| `payment.api.base-url` | `PAYMENT_API_BASE_URL` | — | Base URL upstream API |
| `payment.api.access-token-path` | `PAYMENT_API_ACCESS_TOKEN_PATH` | — | Path endpoint OAuth2 token |
| `payment.api.inquiry-path` | `PAYMENT_API_INQUIRY_PATH` | — | Path endpoint bill inquiry |
| `payment.api.healthz-path` | `PAYMENT_API_HEALTHZ_PATH` | `/v2/bill/healthz` | Path endpoint health check |
| `payment.api.connect-timeout` | `PAYMENT_API_CONNECT_TIMEOUT` | `10s` | Timeout koneksi TCP |
| `payment.api.read-timeout` | `PAYMENT_API_READ_TIMEOUT` | `20s` | Timeout baca response |

### `payment.auth.*` — Auth headers

| Property | Env Variable | Default | Deskripsi |
|---|---|---|---|
| `payment.auth.client-key` | `PAYMENT_CLIENT_KEY` | — | `X-CLIENT-KEY` header (token request) |
| `payment.auth.channel-id` | `PAYMENT_CHANNEL_ID` | — | `CHANNEL-ID` header (bill request) |
| `payment.auth.partner-id` | `PAYMENT_PARTNER_ID` | — | `X-PARTNER-ID` header |
| `payment.auth.external-id` | `PAYMENT_EXTERNAL_ID` | — | `X-EXTERNAL-ID` header |
| `payment.auth.signature` | `PAYMENT_SIGNATURE` | — | `X-SIGNATURE` header |
| `payment.auth.timestamp` | `PAYMENT_AUTH_TIMESTAMP` | — | `X-TIMESTAMP` header (format: `yyyy-MM-ddTHH:mm:ssxxx`) |

### Contoh `application.yml`

```yaml
server:
  port: 8081

payment:
  api:
    base-url: https://payment.uii.ac.id
    access-token-path: /v1.0/access-token/b2b/
    inquiry-path: /v2/bill/inquiry
    healthz-path: /v2/bill/healthz
    connect-timeout: 10s
    read-timeout: 20s
  auth:
    client-key: ${PAYMENT_CLIENT_KEY}
    channel-id: ${PAYMENT_CHANNEL_ID}
    partner-id: ${PAYMENT_PARTNER_ID}
    external-id: ${PAYMENT_EXTERNAL_ID}
    signature: ${PAYMENT_SIGNATURE}
    timestamp: ${PAYMENT_AUTH_TIMESTAMP}

logging:
  level:
    com.uii.paymently: DEBUG
    org.apache.http: INFO
```

## Menjalankan Aplikasi

```bash
# Pastikan JAVA_HOME ke JDK 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Set kredensial (ganti dengan nilai sebenarnya)
export PAYMENT_CLIENT_KEY="..."
export PAYMENT_CHANNEL_ID="..."
export PAYMENT_PARTNER_ID="..."
export PAYMENT_EXTERNAL_ID="..."
export PAYMENT_SIGNATURE="..."
export PAYMENT_AUTH_TIMESTAMP="2026-07-14T15:44:00+07:00"

# Jalankan
mvn spring-boot:run
```

Aplikasi berjalan di **`http://localhost:8081`**.

## API Endpoints

### 1. Internal Liveness (`/healthz`)

```
GET /api/v1/bill/healthz
```

Cek kesehatan internal — **tidak memanggil upstream API**. Tidak memerlukan auth atau header apapun.

```bash
curl --request GET --url http://localhost:8081/api/v1/bill/healthz
```

**Response (200):**

```json
{
  "status": "UP",
  "service": "paymently"
}
```

### 2. Upstream Health Check (`/health`)

```
GET /api/v1/bill/health
```

Meneruskan health check ke upstream `/v2/bill/healthz`. Mendukung **header opsional** untuk meng-override nilai dari konfigurasi:

| Header | Jika dikirim | Jika tidak dikirim |
|---|---|---|
| `X-CLIENT-KEY` | Dipakai sebagai `CHANNEL-ID` + `X-CLIENT-KEY` (token) | Pakai default `payment.auth.*` |
| `X-TIMESTAMP` | Dipakai sebagai `X-TIMESTAMP` | Pakai default `payment.auth.timestamp` |
| `X-EXTERNAL-ID` | Dipakai sebagai `X-EXTERNAL-ID` | Pakai default `payment.auth.external-id` |

> **Catatan:** Jika header override dikirim, cache token di-bypass — token baru di-fetch dengan kredensial yang di-override.

```bash
# Dengan header custom
curl --request GET \
  --url http://localhost:8081/api/v1/bill/health \
  --header 'X-CLIENT-KEY: KPBW' \
  --header 'X-TIMESTAMP: 2026-08-01T10:30:00+07:00' \
  --header 'X-EXTERNAL-ID: KPBW'

# Tanpa header (pakai default dari config)
curl --request GET --url http://localhost:8081/api/v1/bill/health
```

**Response Sukses (200):**

```json
{
  "status": "OK",
  "time": "2026-08-01T13:55:32Z"
}
```

**Response Timeout (504 Gateway Timeout):**

```json
{
  "timestamp": "2026-08-01 10:30:00",
  "status": 504,
  "error": "Gateway Timeout",
  "message": "Token endpoint returned HTTP 400: ...",
  "path": "/api/v1/bill/health"
}
```

### 3. Bill Inquiry (`/inquiry`)

```
POST /api/v1/bill/inquiry
Content-Type: application/json
```

Memeriksa tagihan pelanggan — meneruskan request ke upstream `/v2/bill/inquiry`. Semua _field_ bersifat opsional (`@JsonInclude(NON_NULL)`) dan dikirim apa adanya.

**Full sample:**
```bash
curl --request POST \
  --url http://localhost:8081/api/v1/bill/inquiry \
  --header 'Content-Type: application/json' \
  --data '{
    "partnerServiceId": "04602",
    "customerNo": "0226016324",
    "virtualAccountNo": "046020226016324",
    "trxDateInit": "2026-08-04T10:30:00+07:00",
    "channelCode": 6011,
    "language": "ID",
    "amount": 500000,
    "hashedSourceAccountNo": "d52e42f3a8b1c9e7f6d5a4b3c2e1f0a9",
    "sourceBankCode": "008",
    "passApp": "123456",
    "inquiryRequestId": "INQ-20260804-001",
    "paymentRequestId": "PAY-20260804-001",
    "additionalInfo": {
      "deviceId": "BSIUII"
    }
  }'
```

**Minimal:**
```bash
curl -X POST http://localhost:8081/api/v1/bill/inquiry \
  -H 'Content-Type: application/json' \
  -d '{"customerNo":"0226016324"}'
```

**Response Sukses (200):**

```json
{
  "responseCode": "00",
  "responseMessage": "Success"
}
```

**Response Timeout (504):**

```json
{
  "timestamp": "2026-08-01 10:30:00",
  "status": 504,
  "error": "Gateway Timeout",
  "message": "I/O error on POST request for \"...\": Read timed out",
  "path": "/api/v1/bill/inquiry"
}
```

### 4. Request Log (`/requests`)

```
GET /api/v1/bill/requests
```

Mengembalikan history request yang tercatat oleh `RequestLoggingFilter` (max 200 entry). Digunakan oleh dashboard.

```bash
curl http://localhost:8081/api/v1/bill/requests | python3 -m json.tool
```

**Response (200):**

```json
[
  {
    "timestamp": "2026-08-04T10:30:00Z",
    "clientIp": "127.0.0.1",
    "method": "GET",
    "uri": "/api/v1/bill/healthz",
    "requestHeaders": {"host": "localhost:8081", "user-agent": "curl/8.x"},
    "requestBody": "",
    "responseStatus": 200,
    "responseHeaders": {"Content-Type": "application/json"},
    "responseBody": "{\"status\":\"UP\",\"service\":\"paymently\"}",
    "durationMs": 12
  }
]
```

> **Catatan:** Endpoint `/api/v1/bill/requests` sendiri **tidak dicatat** di log untuk menghindari _self-monitoring loop_.

## Alur Autentikasi

Paymently menggunakan **OAuth2 Client Credentials** untuk mengakses upstream API:

```
1. Request masuk → POST /api/v1/bill/inquiry (atau GET /health)
2. Paymently cek cache token → kalau masih valid, langsung pakai (skip step 3-5)
3. Paymently → POST /v1.0/access-token/b2b/
     Headers: X-CLIENT-KEY, X-SIGNATURE, X-TIMESTAMP, X-EXTERNAL-ID
     Body:   {"grantType":"client_credentials"}
4. Upstream → {"accessToken":"...","tokenType":"Bearer","expiresIn":"3600"}
5. Token di-cache di memori dengan buffer 60 detik sebelum expiry
6. Paymently → POST /v2/bill/inquiry
     Headers: Authorization: Bearer <token>, CHANNEL-ID, X-PARTNER-ID, X-SIGNATURE, ...
7. Response dikembalikan ke pemanggil
```

Token di-cache secara _in-memory_ (`volatile` String + `Instant` expiry) dan otomatis di-refresh saat mendekati expiry. Jika client mengirim header override (`X-CLIENT-KEY`, dll), **cache di-bypass** dan token baru di-fetch.

## Monitoring & Dashboard

Paymently menyertakan _cron job_ + dashboard HTML untuk memonitor healthz endpoint secara berkala.

### Dashboard

Dashboard menampilkan statistik API secara _real-time_ di **http://localhost:9090**. Data berasal dari Request Log (`/api/v1/bill/requests`), mencakup semua traffic API + probe Uptime Kuma.

- **Live status dot** — hijau (2xx) / merah (non-2xx) berdasarkan request terakhir
- **3 KPI tiles** — Total Requests, Success (2xx, dengan success rate %), Failure (4xx/5xx)
- **Last request bar** — Method, path, HTTP code, response body, duration
- **Failures table** — Request error dengan pagination (10/50/100 per page), klik row untuk expand detail (headers + body lengkap)
- **Request Log** — Semua request dengan pagination (10/50/100), klik row untuk expand detail
- **Export PDF** — Tombol ⎙ PDF di header (via browser print → Save as PDF)
- **Dark mode** — toggle ☀︎/☾, auto-detect OS preference
- **Auto-refresh** — fetch data setiap 30 detik

```bash
# Jalankan server monitor (port 9090)
python3 monitor-server.py &

# Buka di browser
open http://localhost:9090

# Hentikan server
lsof -ti:9090 | xargs kill
```

### Menjadwalkan Health Check Manual

```bash
# Sekali jalan — hasil di-append ke logs/healthz-monitor.log
./healthz-check.sh
```

### File Monitoring

```
├── docker-compose.yml        # Uptime Kuma container
├── monitor-server.py         # Dashboard server (port 9090)
├── dashboard.html            # Dashboard UI
├── healthz-check.sh          # Script curl → log JSON line
└── logs/
    └── healthz-monitor.log   # Log hasil monitoring (gitignored)
```

### Uptime Kuma (External Monitoring)

Uptime Kuma dikelola di project terpisah: **`/Users/bsi-2500011/Project/AI/uptime_kuma/`**

```bash
cd /Users/bsi-2500011/Project/AI/uptime_kuma
docker compose up -d
open http://localhost:3001
```

## Pengujian

```bash
# Semua test
mvn test

# Satu kelas
mvn test -Dtest=PaymentMiddlewareServiceTest

# Satu metode
mvn test -Dtest=PaymentMiddlewareServiceTest#shouldReturnHealthz

# Build tanpa test
mvn clean package -DskipTests
```

### Cakupan Test

| Test | Tipe | Cakupan |
|---|---|---|
| `RestTemplateConfigTest` | Integrasi | Verifikasi bean RestTemplate menggunakan Apache HttpClient 5 |
| `PaymentMiddlewareServiceTest` | Integrasi (WireMock) | 4 skenario: inquiry sukses, inquiry timeout, healthz sukses, token timeout |

Test integrasi menggunakan **WireMock** (standalone, dynamic port) untuk menyimulasikan upstream API. `@DynamicPropertySource` meng-override `payment.api.*` agar mengarah ke WireMock. Test config berada di `src/test/resources/application.yml` dengan nilai dummy yang aman.

## Logging

Log dalam format **JSON** (Logstash Logback Encoder 8.0), output ke:

| Output | Path | Level |
|---|---|---|
| Console | stdout | `com.uii.paymently: DEBUG`, `org.apache.http: INFO` |
| Main log | `logs/paymently.json` | Rolling file, semua event |
| Healthz events | `logs/payment.json` | Khusus logger `payment.healthz` |

Header `Authorization` otomatis di-redact di log (diganti `Bearer ****`).

## Teknologi

| Teknologi | Keterangan |
|---|---|
| Java 21 | Runtime |
| Spring Boot 3.4.1 | Framework |
| Apache HttpClient 5 | HTTP client (_connection pooling_: max 20 total, 10 per route) |
| WireMock 3.9 | HTTP stub untuk pengujian integrasi |
| Logstash Logback 8.0 | _Structured JSON logging_ |
| Lombok | _Boilerplate_ reduction (builder, data, constructor) |
| Maven | Build tool |

## Struktur Proyek

```
src/main/java/com/uii/paymently/
├── PaymentlyApplication.java          # Entry point
├── config/
│   └── RestTemplateConfig.java        # Bean RestTemplate (Apache HttpClient 5)
├── controller/
│   └── BillController.java            # 4 endpoint: healthz, health, inquiry, requests
├── dto/
│   ├── AccessTokenRequest.java        # OAuth2 request body
│   ├── AccessTokenResponse.java       # OAuth2 response body
│   ├── AdditionalInfo.java            # Nested DTO (deviceId)
│   ├── BillInquiryRequest.java        # Request body bill inquiry
│   ├── BillInquiryResponse.java       # Response body bill inquiry
│   └── ErrorResponse.java             # Standard error envelope
├── exception/
│   └── GlobalExceptionHandler.java    # 504 & 500 handler
├── filter/
│   ├── RequestLogStore.java           # In-memory ring buffer (200 entry)
│   └── RequestLoggingFilter.java      # Tangkap request/response detail
└── service/
    ├── PaymentMiddlewareService.java  # Proxy bill inquiry + health
    └── TokenService.java              # OAuth2 token + in-memory cache

src/test/
├── java/com/uii/paymently/
│   ├── config/RestTemplateConfigTest.java
│   └── service/PaymentMiddlewareServiceTest.java
└── resources/
    └── application.yml                # Test config (nilai dummy)

├── Dockerfile                         # Paymently container
├── Dockerfile.dashboard               # Dashboard container
├── docker-compose.paymently.yml       # Paymently + Dashboard
├── healthz-check.sh                   # Monitoring: curl script
├── monitor-server.py                  # Monitoring: dashboard server
├── dashboard.html                     # Monitoring: dashboard UI
├── application-example.yml            # Template konfigurasi
└── CLAUDE.md                          # Petunjuk Claude Code
```
