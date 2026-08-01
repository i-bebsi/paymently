package com.uii.paymently.controller;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import com.uii.paymently.service.PaymentMiddlewareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
}
