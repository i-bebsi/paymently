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
import static org.assertj.core.api.Assertions.assertThat;
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
                        .withFixedDelay(25000)));  // 25 detik — read timeout 20s

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