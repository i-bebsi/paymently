# Paymently — Payment API Middleware Connector (Spring Boot)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Build a Java Spring Boot middleware project yang menghubungkan ke `payment.uii.ac.id/v2/bill/*` API dengan structured JSON logging (timestamp, thread, log_message) dan graceful handling saat terjadi timeout/koneksi gagal.

**Architecture:** Spring Boot 3.x + RestTemplate + Apache HttpClient 5 + Logback JSON (logstash-logback-encoder). Single-module Maven project dengan satu controller (REST endpoint untuk testing), satu service class `MiddlewareConnector` yang menangani HTTP call, custom `RestTemplate` bean dengan timeout config, dan structured logging via `logback-spring.xml`.

**Tech Stack:** Java 21, Spring Boot 3.4.x, Maven 3.9+, Apache HttpClient 5, Logstash Logback Encoder 8.x, Lombok, Jackson.

---

## Prasyarat (belum terinstall — perlu dijalankan sebelum Task 1)

```bash
# Install Java 21 via Homebrew
brew install openjdk@21
# Symlink agar tersedia di PATH
sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
export JAVA_HOME=$(brew --prefix openjdk@21)
# Verifikasi
java -version  # Harus muncul "21.x.x"

# Install Maven
brew install maven
mvn -version   # Harus muncul "3.9.x"
```

---

## Struktur Project (setelah selesai)

```
paymently/
├── .gitignore
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/uii/paymently/
│   │   │   ├── PaymentlyApplication.java
│   │   │   ├── config/
│   │   │   │   └── RestTemplateConfig.java
│   │   │   ├── controller/
│   │   │   │   └── BillController.java
│   │   │   ├── dto/
│   │   │   │   ├── BillInquiryRequest.java
│   │   │   │   ├── BillInquiryResponse.java
│   │   │   │   └── ErrorResponse.java
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   └── service/
│   │   │       └── PaymentMiddlewareService.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/uii/paymently/
│           ├── config/
│           │   └── RestTemplateConfigTest.java
│           └── service/
│               └── PaymentMiddlewareServiceTest.java
```

---

### Task 1: Generate Spring Boot project skeleton via Maven archetype

**Objective:** Buat struktur project Maven dengan Spring Boot parent.

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/uii/paymently/PaymentlyApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `.gitignore`

**Step 1: Tulis pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.uii</groupId>
    <artifactId>paymently</artifactId>
    <version>1.0.0</version>
    <name>paymently</name>
    <description>Payment API Middleware Connector for UII</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Logstash JSON encoder untuk structured logging -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>8.0</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Apache HttpClient 5 (untuk timeout config granular) -->
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- WireMock untuk mock HTTP server di test -->
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.9.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: Tulis main class**

`src/main/java/com/uii/paymently/PaymentlyApplication.java`:

```java
package com.uii.paymently;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentlyApplication.class, args);
    }
}
```

**Step 3: Tulis application.yml & .gitignore**

`src/main/resources/application.yml`:

```yaml
server:
  port: 8081

payment:
  api:
    base-url: https://payment.uii.ac.id
    inquiry-path: /v2/bill/inquiry
    payment-path: /v2/bill/payment
    connect-timeout: 10s
    read-timeout: 20s
    # ↑ bisa di-override via env var PAYMENT_API_READ_TIMEOUT, mis: "5s", "60s"
  auth:
    bearer-token: ${PAYMENT_BEARER_TOKEN:eyJ0eXAiOiJ...}
    channel-id: BCAS
    partner-id: BCAS
    external-id: BCAS
    signature: ${PAYMENT_SIGNATURE:}

logging:
  level:
    com.uii.paymently: DEBUG
    org.apache.http: INFO
```

`.gitignore`:

```
target/
!.mvn/wrapper/maven-wrapper.jar
*.class
*.jar
*.war
.idea/
*.iml
.DS_Store
```

**Step 4: Build & verifikasi**

```bash
cd /Users/bsi-2500011/Project/AI/paymently
mvn clean compile -q
```

Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git init
git add -A
git commit -m "init: spring boot project skeleton with dependencies"
```

---

### Task 2: Structured JSON logging via logback-spring.xml

**Objective:** Konfigurasi logback agar output log dalam format JSON structured: `{timestamp, thread, log_message}`.

**Files:**
- Create: `src/main/resources/logback-spring.xml`

**Step 1: Tulis logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- JSON encoder yang dipakai bersama -->
    <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Custom fields: hanya timestamp, thread, message (tanpa logger_name terpisah) -->
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <thread>thread</thread>
                <message>log_message</message>
                <logger>logger</logger>
                <level>level</level>
            </fieldNames>
            <timestampPattern>yyyy-MM-dd HH:mm:ss</timestampPattern>
        </encoder>
    </appender>

    <!-- JSON untuk file persistent -->
    <appender name="FILE_JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/paymently.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/paymently.%d{yyyy-MM-dd}.json</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <thread>thread</thread>
                <message>log_message</message>
                <level>level</level>
            </fieldNames>
            <timestampPattern>yyyy-MM-dd HH:mm:ss</timestampPattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE_JSON" />
        <appender-ref ref="FILE_JSON" />
    </root>

    <logger name="com.uii.paymently" level="DEBUG"/>
    <logger name="com.uii.paymently.service" level="DEBUG"/>
    <logger name="org.apache.http" level="INFO"/>
</configuration>
```

**Step 2: Verifikasi bahwa logback menghasilkan log JSON**

Jalankan aplikasi sebentar — tekan Ctrl+C setelah startup:

```bash
cd /Users/bsi-2500011/Project/AI/paymently
mvn spring-boot:run &
PID=$!
sleep 8
kill $PID 2>/dev/null
```

Expected: Console output berupa JSON lines dengan field `timestamp`, `thread`, `log_message`.

**Step 3: Commit**

```bash
git add src/main/resources/logback-spring.xml
git commit -m "feat: structured JSON logging with logstash-logback-encoder"
```

---

### Task 3: DTO classes (request & response)

**Objective:** Buat POJO untuk request dan response body API payment.

**Files:**
- Create: `src/main/java/com/uii/paymently/dto/BillInquiryRequest.java`
- Create: `src/main/java/com/uii/paymently/dto/BillInquiryResponse.java`
- Create: `src/main/java/com/uii/paymently/dto/ErrorResponse.java`
- Create: `src/main/java/com/uii/paymently/dto/AdditionalInfo.java`

**Step 1: Tulis AdditionalInfo.java**

```java
package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdditionalInfo {
    private String deviceId;
}
```

**Step 2: Tulis BillInquiryRequest.java**

```java
package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillInquiryRequest {

    private String partnerServiceId;
    private String customerNo;
    private String virtualAccountNo;
    private String trxDateInit;
    private Integer channelCode;
    private String language;
    private BigDecimal amount;
    private String hashedSourceAccountNo;
    private String sourceBankCode;
    private String passApp;
    private String inquiryRequestId;
    private String paymentRequestId;
    private AdditionalInfo additionalInfo;
}
```

**Step 3: Tulis BillInquiryResponse.java** (generic — hanya wrapper)

```java
package com.uii.paymently.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillInquiryResponse {
    private String responseCode;
    private String responseMessage;
}
```

**Step 4: Tulis ErrorResponse.java**

```java
package com.uii.paymently.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
```

**Step 5: Compile**

```bash
cd /Users/bsi-2500011/Project/AI/paymently
mvn clean compile -q
```

Expected: BUILD SUCCESS

**Step 6: Commit**

```bash
git add src/main/java/com/uii/paymently/dto/
git commit -m "feat: add DTO classes for bill inquiry request/response"
```

---

### Task 4: RestTemplate configuration dengan timeout & interceptor

**Objective:** Buat RestTemplate bean dengan Apache HttpClient 5, timeout config (connect 10s, read 20s — configurable via env), dan interceptor untuk logging header/URL.

**Files:**
- Create: `src/main/java/com/uii/paymently/config/RestTemplateConfig.java`

**Step 1: Tulis RestTemplateConfig.java**

```java
package com.uii.paymently.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest as SpringHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${payment.api.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${payment.api.read-timeout:30s}")
    private Duration readTimeout;

    @Bean
    public RestTemplate paymentRestTemplate(RestTemplateBuilder builder) {
        // Apache HttpClient 5 connection manager
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);
        connectionManager.setDefaultMaxPerRoute(10);

        // Interceptor untuk logging request
        HttpRequestInterceptor requestInterceptor = (HttpRequest request, org.apache.hc.core5.http.EntityDetails entity, org.apache.hc.core5.http.protocol.HttpContext context) -> {
            log.debug("=== HEADER ===");
            for (Header header : request.getHeaders()) {
                log.debug("{}: {}", header.getName(),
                        header.getName().equalsIgnoreCase("Authorization")
                                ? "Bearer ****"
                                : header.getValue());
            }
            log.debug("=== URL {} ===", request.getRequestUri());
        };

        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .addRequestInterceptorFirst(requestInterceptor)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(connectTimeout);
        factory.setConnectionRequestTimeout(connectTimeout);

        RestTemplate restTemplate = builder
                .requestFactory(() -> factory)
                .setReadTimeout(readTimeout)
                .build();

        return restTemplate;
    }
}
```

**Step 2: Compile**

```bash
mvn clean compile -q
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/uii/paymently/config/
git commit -m "feat: RestTemplate config with HttpClient 5, timeout, and request logging interceptor"
```

---

### Task 5: PaymentMiddlewareService — core HTTP call logic

**Objective:** Implement service class yang melakukan POST ke payment API dengan header signature dan handle `ResourceAccessException` (timeout).

**Files:**
- Create: `src/main/java/com/uii/paymently/service/PaymentMiddlewareService.java`

**Step 1: Tulis PaymentMiddlewareService.java**

```java
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
```

**Step 2: Compile**

```bash
mvn clean compile -q
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/uii/paymently/service/
git commit -m "feat: PaymentMiddlewareService with timeout error handling and debug logging"
```

---

### Task 6: GlobalExceptionHandler — status 504 saat timeout

**Objective:** Tangkap exception, return JSON error response dengan HTTP 504 (Gateway Timeout).

**Files:**
- Create: `src/main/java/com/uii/paymently/exception/GlobalExceptionHandler.java`

**Step 1: Tulis GlobalExceptionHandler.java**

```java
package com.uii.paymently.exception;

import com.uii.paymently.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(ResourceAccessException e, HttpServletRequest req) {
        Throwable rootCause = e.getRootCause() != null ? e.getRootCause() : e;
        log.error("Connect to {}:443 failed: {}",
                req.getServerName(),
                rootCause.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().format(FMT))
                .status(HttpStatus.GATEWAY_TIMEOUT.value())
                .error("Gateway Timeout")
                .message(e.getMessage())
                .path(req.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException e, HttpServletRequest req) {
        log.error("Unexpected error: {}", e.getMessage());

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().format(FMT))
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message(e.getMessage())
                .path(req.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
```

**Step 2: Compile**

```bash
mvn clean compile -q
```

Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/uii/paymently/exception/
git commit -m "feat: global exception handler with 504 on timeout"
```

---

### Task 7: BillController — REST endpoint untuk testing

**Objective:** Buat controller `/api/v1/bill/inquiry` yang menerima request, forward ke PaymentMiddlewareService.

**Files:**
- Create: `src/main/java/com/uii/paymently/controller/BillController.java`

**Step 1: Tulis BillController.java**

```java
package com.uii.paymently.controller;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import com.uii.paymently.service.PaymentMiddlewareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/bill")
@RequiredArgsConstructor
public class BillController {

    private final PaymentMiddlewareService middlewareService;

    @PostMapping("/inquiry")
    public ResponseEntity<?> inquiryBill(@RequestBody BillInquiryRequest request) {
        log.info("Received bill inquiry request: customerNo={}", request.getCustomerNo());
        BillInquiryResponse response = middlewareService.inquiryBill(request);
        return ResponseEntity.ok(response);
    }
}
```

**Step 2: Compile & jalankan (quick smoke test — hanya pastikan app start)**

```bash
mvn clean package -DskipTests -q
java -jar target/paymently-1.0.0.jar &
PID=$!
sleep 8
curl -s http://localhost:8081/actuator/health 2>/dev/null || echo "(actuator tidak ada — expected, cek startup log)"
kill $PID 2>/dev/null
```

No actuator by default — pastikan tidak ada error di startup.

**Step 3: Commit**

```bash
git add src/main/java/com/uii/paymently/controller/
git commit -m "feat: BillController REST endpoint for bill inquiry"
```

---

### Task 8: Unit test — RestTemplate timeout configuration

**Objective:** Verifikasi bahwa RestTemplate bean memiliki timeout yang benar.

**Files:**
- Create: `src/test/java/com/uii/paymently/config/RestTemplateConfigTest.java`

**Step 1: Tulis test**

```java
package com.uii.paymently.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RestTemplateConfigTest {

    @Autowired
    @Qualifier("paymentRestTemplate")
    private RestTemplate restTemplate;

    @Test
    void shouldConfigureConnectTimeout() {
        var factory = (HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertThat(factory.getConnectTimeout())
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldCreateRestTemplateBean() {
        assertThat(restTemplate).isNotNull();
    }
}
```

**Step 2: Run test**

```bash
mvn test -pl . -Dtest=RestTemplateConfigTest -DfailIfNoTests=false -q
```

Expected: 2 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/uii/paymently/config/
git commit -m "test: verify RestTemplate timeout configuration"
```

---

### Task 9: Integration test — timeout scenario dengan WireMock

**Objective:** Simulasikan timeout menggunakan WireMock (delay melampaui readTimeout) dan pastikan error log muncul dalam format JSON.

**Files:**
- Create: `src/test/java/com/uii/paymently/service/PaymentMiddlewareServiceTest.java`

**Step 1: Tulis integration test**

```java
package com.uii.paymently.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.uii.paymently.dto.AdditionalInfo;
import com.uii.paymently.dto.BillInquiryRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentMiddlewareServiceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private PaymentMiddlewareService service;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.api.base-url", () -> wireMockServer.baseUrl());
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void shouldThrowExceptionOnTimeout() {
        // Setup WireMock stub dengan delay > read timeout (20s+)
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/inquiry"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\"}")
                        .withFixedDelay(25000)));  // 25 detik — read timeout 20s (bisa di-override via PAYMENT_API_READ_TIMEOUT)

        BillInquiryRequest request = BillInquiryRequest.builder()
                .partnerServiceId("04602")
                .customerNo("0226016324")
                .virtualAccountNo("046020226016324")
                .channelCode(6011)
                .language("ID")
                .sourceBankCode("008")
                .inquiryRequestId("test-request-id")
                .additionalInfo(AdditionalInfo.builder().deviceId("BSIUII").build())
                .build();

        // Harus throw RuntimeException karena timeout
        assertThatThrownBy(() -> service.inquiryBill(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connect to")
                .hasMessageContaining("failed");
    }

    @Test
    void shouldSucceedOnNormalResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/inquiry"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\",\"responseMessage\":\"Success\"}")));

        BillInquiryRequest request = BillInquiryRequest.builder()
                .partnerServiceId("04602")
                .customerNo("0226016324")
                .virtualAccountNo("046020226016324")
                .channelCode(6011)
                .language("ID")
                .sourceBankCode("008")
                .inquiryRequestId("test-request-id")
                .additionalInfo(AdditionalInfo.builder().deviceId("BSIUII").build())
                .build();

        var response = service.inquiryBill(request);
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Success");
    }
}
```

**Step 2: Run integration test**

```bash
mvn test -pl . -Dtest=PaymentMiddlewareServiceTest -DfailIfNoTests=false
```

Expected:
- `shouldSucceedOnNormalResponse`: PASS
- `shouldThrowExceptionOnTimeout`: PASS (dengan log JSON error di console)

Timeout test akan berjalan ~25 detik karena WireMock delay.

**Step 3: Commit**

```bash
git add src/test/java/com/uii/paymently/service/
git commit -m "test: integration test for timeout and normal inquiry scenarios"
```

---

### Task 10: Final verification — full build & run

**Objective:** Pastikan semua test pass dan aplikasi bisa dijalankan.

**Step 1: Full build with tests**

```bash
cd /Users/bsi-2500011/Project/AI/paymently
mvn clean verify
```

Expected: BUILD SUCCESS, all tests pass.

**Step 2: Jalankan aplikasi & tes dengan curl**

```bash
java -jar target/paymently-1.0.0.jar &
PID=$!
sleep 10

# Kirim request inquiry ke localhost
curl -s -X POST http://localhost:8081/api/v1/bill/inquiry \
  -H 'Content-Type: application/json' \
  -d '{
    "partnerServiceId": "04602",
    "customerNo": "0226016324",
    "virtualAccountNo": "046020226016324",
    "channelCode": 6011,
    "language": "ID",
    "sourceBankCode": "008",
    "inquiryRequestId": "manual-test-001",
    "additionalInfo": {
        "deviceId": "BSIUII"
    }
  }' | python3 -m json.tool

kill $PID 2>/dev/null
```

Jika API payment.uii.ac.id tidak reachable, akan muncul error timeout + JSON log di stdout (format: `{"timestamp":"...","thread":"...","log_message":"..."}`).

Jika reachable, akan return response dari API upstream.

**Step 3: Final commit (jika ada perubahan)**

```bash
git add -A
git commit -m "chore: final verification and cleanup"
```

---

## Cara Menjalankan (setelah implementasi)

```bash
# Set environment variables
export PAYMENT_BEARER_TOKEN="eyJ0eXAiOiJ..."
export PAYMENT_SIGNATURE="66067A57D3A3D75FE97AC83..."

# Override timeout jika perlu (default: connect=10s, read=20s)
export PAYMENT_API_CONNECT_TIMEOUT="10s"
export PAYMENT_API_READ_TIMEOUT="20s"

# Jalankan
cd /Users/bsi-2500011/Project/AI/paymently
mvn spring-boot:run

# Atau build & run jar
mvn clean package -DskipTests
java -jar target/paymently-1.0.0.jar
```

Test endpoint:

```bash
curl -X POST http://localhost:8081/api/v1/bill/inquiry \
  -H 'Content-Type: application/json' \
  -d '{
    "partnerServiceId": "04602",
    "customerNo": "0226016324",
    "virtualAccountNo": "046020226016324",
    "channelCode": 6011,
    "language": "ID",
    "sourceBankCode": "008",
    "inquiryRequestId": "test-001",
    "additionalInfo": {"deviceId": "BSIUII"}
  }'
```

---

## Output Log Saat Timeout (contoh)

```json
{"timestamp":"2026-07-15 12:30:15","thread":"http-nio-8081-exec-1","log_message":"DEBUG com.uii.paymently.service.PaymentMiddlewareService === HEADER ===","level":"DEBUG"}
{"timestamp":"2026-07-15 12:30:15","thread":"http-nio-8081-exec-1","log_message":"DEBUG com.uii.paymently.service.PaymentMiddlewareService === URL https://payment.uii.ac.id/v2/bill/inquiry ===","level":"DEBUG"}
{"timestamp":"2026-07-15 12:30:45","thread":"http-nio-8081-exec-1","log_message":"ERROR com.uii.paymently.service.PaymentMiddlewareService Connect to payment.uii.ac.id:443 failed: Operation timed out (Connection timed out)","level":"ERROR"}
```

---

## Risiko & Open Questions

1. **Java belum terinstall** — perlu `brew install openjdk@21` + Maven sebelum memulai.
2. **Bearer token & signature sudah expired** — nilai di `application.yml` adalah placeholder; real credentials harus di-set via environment variable.
3. **API payment upstream mungkin menggunakan SSL cert khusus** — jika muncul `SSLHandshakeException`, perlu menambahkan truststore atau nonaktifkan SSL verification (tidak direkomendasikan untuk production).
4. **WireMock integration test** lambat karena `shouldThrowExceptionOnTimeout` butuh ~35 detik. Bisa di-skip saat build CI dengan `@Tag("slow")`.

---

## Execution Handoff

Plan complete. 10 tasks, semuanya bite-sized (2-5 menit per task), TDD di mana relevan, exact file paths, copy-pasteable code. Ready to execute.
