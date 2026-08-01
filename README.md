# Paymently

**Payment API Middleware Connector for UII**

Paymently adalah _middleware_ Spring Boot yang menjembatani aplikasi internal UII dengan Payment API Gateway eksternal (`payment.uii.ac.id`). Layanan ini menangani autentikasi OAuth2 secara otomatis, meneruskan (_proxy_) permintaan _bill inquiry_ dan _health check_, serta memberikan penanganan _error_ yang terstruktur.

## Fitur

- **OAuth2 Client Credentials** — Mengambil _access token_ secara dinamis dari `/v1.0/access-token/b2b` dan menyimpannya di _cache_ hingga mendekati masa kedaluwarsa.
- **Bill Inquiry** — Meneruskan permintaan pemeriksaan tagihan ke upstream `/v2/bill/inquiry`.
- **Health Check** — Meneruskan pengecekan kesehatan layanan ke upstream `/v2/bill/healthz`.
- **Timeout Handling** — Koneksi _timeout_ upstream dikembalikan sebagai HTTP 504 Gateway Timeout dengan pesan yang jelas.
- **Structured Logging** — Log dalam format JSON (Logstash) ke konsol dan file (`logs/paymently.json`).

## Teknologi

| Teknologi | Keterangan |
|-----------|------------|
| Java 21 | Runtime |
| Spring Boot 3.4.1 | Framework |
| Apache HttpClient 5 | HTTP client dengan _connection pooling_ |
| WireMock 3.9 | _HTTP stub_ untuk pengujian integrasi |
| Logstash Logback | _Structured JSON logging_ |
| Lombok | _Boilerplate_ reduction |
| Maven | Build tool |

## Prasyarat

- **Java 21** — Lombok _annotation processor_ tidak kompatibel dengan Java 26+. Pastikan `JAVA_HOME` mengarah ke JDK 21:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # macOS Homebrew
  ```
- **Maven 3.9+**

## Konfigurasi

Konfigurasi berada di `src/main/resources/application.yml`. Nilai _default_ dapat di-_override_ melalui _environment variable_:

| Env Variable | Default | Keterangan |
|--------------|---------|------------|
| `PAYMENT_CLIENT_KEY` | _(kosong)_ | `X-CLIENT-KEY` header untuk _access token_ request |
| `PAYMENT_CHANNEL_ID` | _(kosong)_ | `CHANNEL-ID` header untuk request ke upstream |
| `PAYMENT_PARTNER_ID` | _(kosong)_ | `X-PARTNER-ID` header untuk request ke upstream |
| `PAYMENT_EXTERNAL_ID` | _(kosong)_ | `X-EXTERNAL-ID` header untuk semua request |
| `PAYMENT_SIGNATURE` | _(kosong)_ | `X-SIGNATURE` header untuk semua request ke upstream |
| `PAYMENT_AUTH_TIMESTAMP` | _(kosong)_ | `X-TIMESTAMP` header — format: `yyyy-MM-ddTHH:mm:ssxxx` |

_Property_ lain di `application.yml`:

```yaml
payment:
  api:
    base-url: https://payment.uii.ac.id
    connect-timeout: 10s     # timeout koneksi TCP
    read-timeout: 20s        # timeout baca response
```

## Menjalankan Aplikasi

```bash
# Pastikan JAVA_HOME ke JDK 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Jalankan
mvn spring-boot:run
```

Aplikasi berjalan di `http://localhost:8081`.

## Endpoint API

### 1. Bill Inquiry

Memeriksa tagihan pelanggan.

```
POST /api/v1/bill/inquiry
Content-Type: application/json
```

**Request Body:**

```json
{
  "partnerServiceId": "04602",
  "customerNo": "0226016324",
  "virtualAccountNo": "046020226016324",
  "trxDateInit": "2026-07-14T15:44:00+07:00",
  "channelCode": 6011,
  "language": "ID",
  "amount": 500000,
  "hashedSourceAccountNo": "...",
  "sourceBankCode": "008",
  "passApp": "...",
  "inquiryRequestId": "REQ-001",
  "paymentRequestId": "PAY-001",
  "additionalInfo": {
    "deviceId": "BSIUII"
  }
}
```

Semua _field_ bersifat opsional (_nullable_) dan akan dikirim apa adanya ke upstream.

**Response Sukses (200):**

```json
{
  "responseCode": "00",
  "responseMessage": "Success"
}
```

**Response Timeout (504 Gateway Timeout):**

```json
{
  "timestamp": "2026-08-01 10:30:00",
  "status": 504,
  "error": "Gateway Timeout",
  "message": "I/O error on POST request for \"...\": Read timed out",
  "path": "/api/v1/bill/inquiry"
}
```

### 2. Health Check

Memeriksa kesehatan koneksi ke upstream Payment API.

```
GET /api/v1/bill/health
```

**Optional Headers** (meng-override nilai default dari `application.yml`):

| Header | Default (dari config) | Meng-override |
|---|---|---|
| `X-CLIENT-KEY` | `${PAYMENT_CHANNEL_ID}` | `CHANNEL-ID` + `X-CLIENT-KEY` (token) |
| `X-TIMESTAMP` | `${PAYMENT_AUTH_TIMESTAMP}` | `X-TIMESTAMP` |
| `X-EXTERNAL-ID` | `${PAYMENT_EXTERNAL_ID}` | `X-EXTERNAL-ID` |

```bash
curl --request GET \
  --url http://localhost:8081/api/v1/bill/health \
  --header 'X-CLIENT-KEY: KPBW' \
  --header 'X-TIMESTAMP: 2026-08-01T10:30:00+07:00' \
  --header 'X-EXTERNAL-ID: KPBW'
```

**Response Sukses (200):**

```json
{
  "status": "OK",
  "time": "2026-08-01T13:55:32Z"
}
```

_Response body bersifat dinamis — dikembalikan apa adanya dari upstream `/v2/bill/healthz`._

## Alur Autentikasi

```
1. Aplikasi internal → POST /api/v1/bill/inquiry
2. Paymently → POST /v1.0/access-token/b2b   (minta token OAuth2)
3. Upstream → {"accessToken":"...", "expiresIn":"3600"}
4. Paymently → cache token di memori (berlaku ~59 menit)
5. Paymently → POST /v2/bill/inquiry           (pakai token dari cache)
6. Upstream → {"responseCode":"00", ...}
7. Paymently → 200 OK ke aplikasi internal
```

Pada permintaan berikutnya, langkah 2–3 dilewati selama token di _cache_ masih berlaku.

## Monitoring & Dashboard

Paymently menyediakan _cron job_ untuk memonitor endpoint healthz secara berkala beserta dashboard HTML untuk melihat hasilnya.

### Menjalankan Cron Job

Cron job berjalan di dalam sesi Claude Code. Minta Claude untuk menjalankannya:

```
jalankan cron setiap 3 menit untuk curl healthz dengan header X-CLIENT-KEY: KPBW,
X-TIMESTAMP: 2026-08-01T10:30:00+07:00, X-EXTERNAL-ID: KPBW, log hasilnya ke
logs/healthz-monitor.log
```

Atau gunakan _loop mode_:

```
/loop 3m ./healthz-check.sh
```

### Menghentikan Cron Job

Cukup minta Claude untuk menghentikan cron:

```
stop cron healthz
```

Atau matikan Claude Code session untuk menghentikan semua cron sekaligus.

### Menjalankan Dashboard

```bash
# Start monitor server (port 9090)
python3 monitor-server.py &

# Buka di browser
open http://localhost:9090
```

Dashboard menampilkan:
- **Live status dot** — hijau (UP) / merah (DOWN)
- **Stat tiles** — Total Checks, Success (dengan uptime %), Failure
- **Last check bar** — HTTP code + pesan error dari response terakhir
- **Failures table** — 20 kegagalan terakhir: timestamp, HTTP code, pesan
- **Recent checks** — 10 pengecekan terakhir
- **Dark mode** — toggle ☀︎/☾, auto-detect OS preference
- **Auto-refresh** — fetch data setiap 30 detik

### Menghentikan Dashboard

```bash
# Cari PID proses monitor server
lsof -ti:9090 | xargs kill
```

### Menjalankan Health Check Manual

```bash
# Sekali jalan — hasil di-append ke log
./healthz-check.sh

# Lihat log
cat logs/healthz-monitor.log

# Lihat statistik via API
curl -s http://localhost:9090/api/stats | python3 -m json.tool
```

### Format Log

Setiap baris di `logs/healthz-monitor.log` adalah JSON:

```json
{"time":"2026-08-01T13:57:23Z","status":"success","httpCode":200,"body":{"status":"OK"}}
{"time":"2026-08-01T13:58:00Z","status":"failure","httpCode":504,"body":{"error":"Gateway Timeout","message":"..."}}
```

### Struktur File Monitoring

```
├── healthz-check.sh          # Script curl → log JSON line
├── monitor-server.py         # Python HTTP server (port 9090)
├── dashboard.html            # Dashboard UI
└── logs/
    └── healthz-monitor.log   # Log hasil monitoring (gitignored)
```

## Pengujian

```bash
mvn test

# Satu kelas pengujian
mvn test -Dtest=PaymentMiddlewareServiceTest

# Satu metode pengujian
mvn test -Dtest=PaymentMiddlewareServiceTest#shouldReturnHealthz
```

Pengujian integrasi menggunakan WireMock untuk menyimulasikan upstream API, termasuk skenario _timeout_ (token endpoint dan inquiry endpoint).

## Struktur Proyek

```
src/main/java/com/uii/paymently/
├── PaymentlyApplication.java       # Entry point
├── config/
│   └── RestTemplateConfig.java     # Bean RestTemplate (Apache HttpClient 5)
├── controller/
│   └── BillController.java         # REST endpoints
├── dto/
│   ├── AccessTokenRequest.java     # OAuth2 request body
│   ├── AccessTokenResponse.java    # OAuth2 response body
│   ├── AdditionalInfo.java         # Nested DTO
│   ├── BillInquiryRequest.java     # Request body bill inquiry
│   ├── BillInquiryResponse.java    # Response body bill inquiry
│   └── ErrorResponse.java          # Standard error envelope
├── exception/
│   └── GlobalExceptionHandler.java # 504 & 500 handler
└── service/
    ├── PaymentMiddlewareService.java # Logika utama proxy
    └── TokenService.java             # OAuth2 token fetching + cache
```
