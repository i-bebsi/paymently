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