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
| `PAYMENT_CLIENT_KEY` | `BCAS` | `X-CLIENT-KEY` header untuk _access token_ request |
| `PAYMENT_SIGNATURE` | _(kosong)_ | `X-SIGNATURE` header untuk semua request ke upstream |

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

**Response Sukses (200):**

```json
{
  "status": "UP"
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
