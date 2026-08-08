package com.uii.paymently.controller;

import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.BillInquiryResponse;
import com.uii.paymently.dto.PaymentRequest;
import com.uii.paymently.dto.PaymentResponse;
import com.uii.paymently.dto.ReverseRequest;
import com.uii.paymently.dto.ReverseResponse;
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

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live(HttpServletRequest request) {
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

    @PostMapping("/payment")
    public ResponseEntity<?> paymentBill(@RequestBody PaymentRequest request) {
        log.info("Received bill payment request: customerNo={}, virtualAccountNo={}",
                request.getCustomerNo(), request.getVirtualAccountNo());
        PaymentResponse response = middlewareService.paymentBill(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reverse")
    public ResponseEntity<?> reverseBill(@RequestBody ReverseRequest request) {
        log.info("Received bill reverse request: customerNo={}, paymentRequestId={}",
                request.getCustomerNo(), request.getPaymentRequestId());
        ReverseResponse response = middlewareService.reverseBill(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz(
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
