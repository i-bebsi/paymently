package com.uii.paymently.controller;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import com.uii.paymently.filter.RequestLogStore;
import com.uii.paymently.service.PaymentMiddlewareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/bill")
@RequiredArgsConstructor
public class BillController {

    private static final Logger HEALTHZ_LOGGER = LoggerFactory.getLogger("payment.healthz");

    private final PaymentMiddlewareService middlewareService;
    private final RequestLogStore requestLogStore;

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> healthz(HttpServletRequest request) {
        log.info("Healthz request received: method={}, path={}, remoteAddr={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());
        HEALTHZ_LOGGER.info("Healthz request received: method={}, path={}, remoteAddr={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "paymently"
        ));
    }

    @PostMapping("/inquiry")
    public ResponseEntity<?> inquiryBill(@RequestBody BillInquiryRequest request) {
        log.info("Received bill inquiry request: customerNo={}", request.getCustomerNo());
        BillInquiryResponse response = middlewareService.inquiryBill(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestHeader(value = "X-CLIENT-KEY", required = false) String clientKey,
            @RequestHeader(value = "X-TIMESTAMP", required = false) String timestamp,
            @RequestHeader(value = "X-EXTERNAL-ID", required = false) String externalId) {
        log.info("Received health check request (clientKey={}, timestamp={}, externalId={})",
                clientKey, timestamp, externalId);
        Map<String, Object> response = middlewareService.healthz(clientKey, timestamp, externalId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<RequestLogStore.RequestLogEntry>> requestLog() {
        return ResponseEntity.ok(requestLogStore.getAll());
    }
}
