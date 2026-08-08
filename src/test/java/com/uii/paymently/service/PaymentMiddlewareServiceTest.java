package com.uii.paymently.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.uii.paymently.dto.AdditionalInfo;
import com.uii.paymently.dto.Amount;
import com.uii.paymently.dto.BillInquiryRequest;
import com.uii.paymently.dto.PaymentRequest;
import com.uii.paymently.dto.PaymentResponse;
import com.uii.paymently.dto.ReverseRequest;
import com.uii.paymently.dto.ReverseResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.web.client.ResourceAccessException;
import com.uii.paymently.service.TokenService;
import org.springframework.web.client.RestClientResponseException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentMiddlewareServiceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private PaymentMiddlewareService service;

    @Autowired
    private TokenService tokenService;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.api.base-url", () -> wireMockServer.baseUrl());
        registry.add("payment.api.access-token-path", () -> "/v1.0/access-token/b2b/");
        registry.add("payment.api.inquiry-path", () -> "/v2/bill/inquiry");
        registry.add("payment.api.healthz-path", () -> "/v2/bill/healthz");
        registry.add("payment.api.payment-path", () -> "/v2/bill/payment");
        registry.add("payment.api.reverse-path", () -> "/v2/bill/reverse");
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

    @BeforeEach
    void stubAccessToken() {
        // Default: access token endpoint selalu return sukses
        wireMockServer.stubFor(post(urlEqualTo("/v1.0/access-token/b2b/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"mock-access-token\",\"tokenType\":\"Bearer\",\"expiresIn\":\"3600\"}")));
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

        // Harus throw ResourceAccessException karena timeout
        assertThatThrownBy(() -> service.inquiryBill(request))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");
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

    @Test
    void shouldReturnHealthz() {
        wireMockServer.stubFor(get(urlEqualTo("/v2/bill/healthz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));

        var response = service.healthz();
        assertThat(response).containsEntry("status", "UP");
    }

    @Test
    void shouldThrowExceptionOnTokenTimeout() {
        // Reset semua stub + token cache, lalu ganti token endpoint dengan delay > read timeout
        wireMockServer.resetAll();
        tokenService.clearToken();
        wireMockServer.stubFor(post(urlEqualTo("/v1.0/access-token/b2b/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"x\"}")
                        .withFixedDelay(25000)));  // 25 detik

        BillInquiryRequest request = BillInquiryRequest.builder()
                .customerNo("0226016324")
                .build();

        assertThatThrownBy(() -> service.inquiryBill(request))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");
    }

    @Test
    void shouldSucceedOnPayment() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/payment"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\",\"responseMessage\":\"Payment Success\",\"partnerReferenceNo\":\"102000000031571\"}")));

        PaymentRequest request = PaymentRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .virtualAccountNo("019450226032690")
                .virtualAccountName("Mahasiswa H2H")
                .language("ID")
                .paymentRequestId("0696a437-907a-4635-86c2-0df427e55de2")
                .channelCode("6017")
                .sourceBankCode("536")
                .paidAmount(Amount.builder().value("10000.00").currency("IDR").build())
                .trxDateTime("2026-08-08T07:41:29+07:00")
                .totalAmount(Amount.builder().value("1.00").currency("IDR").build())
                .referenceNo("481743469968101")
                .journalNum("87204")
                .additionalInfo("BSIUIIPAY0226032690")
                .build();

        PaymentResponse response = service.paymentBill(request);
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Payment Success");
        assertThat(response.getAdditionalFields()).containsEntry("partnerReferenceNo", "102000000031571");
    }

    @Test
    void shouldThrowExceptionOnPaymentTimeout() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/payment"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\"}")
                        .withFixedDelay(25000)));

        PaymentRequest request = PaymentRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .build();

        assertThatThrownBy(() -> service.paymentBill(request))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");
    }

    @Test
    void shouldSucceedOnReverse() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/reverse"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\",\"responseMessage\":\"Reverse Success\"}")));

        ReverseRequest request = ReverseRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .virtualAccountNo("019450226032690")
                .inquiryRequestId("4ff5816e-5307-426d-8550-2f5f0e801957")
                .paymentRequestId("b6b46175-8a7d-4783-9778-45fb7e122385")
                .originalPartnerReferenceNo("102000000031571")
                .originalReferenceNo("BTNSY1_PAY_1758095290866")
                .originalExternalId("1758095290866")
                .trxDateTime("2026-08-08T07:41:29+07:00")
                .language("ID")
                .amount(Amount.builder().value("1.00").currency("IDR").build())
                .build();

        ReverseResponse response = service.reverseBill(request);
        assertThat(response.getResponseCode()).isEqualTo("00");
        assertThat(response.getResponseMessage()).isEqualTo("Reverse Success");
    }

    @Test
    void shouldThrowExceptionOnReverseTimeout() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/reverse"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"00\"}")
                        .withFixedDelay(25000)));

        ReverseRequest request = ReverseRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .build();

        assertThatThrownBy(() -> service.reverseBill(request))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");
    }

    @Test
    void shouldThrowRestClientResponseExceptionOnPayment400() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/payment"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"99\",\"responseMessage\":\"Invalid amount\"}")));

        PaymentRequest request = PaymentRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .build();

        assertThatThrownBy(() -> service.paymentBill(request))
                .isInstanceOf(RestClientResponseException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }

    @Test
    void shouldThrowRestClientResponseExceptionOnReverse409() {
        wireMockServer.stubFor(post(urlEqualTo("/v2/bill/reverse"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"responseCode\":\"98\",\"responseMessage\":\"Transaction already reversed\"}")));

        ReverseRequest request = ReverseRequest.builder()
                .partnerServiceId("01945")
                .customerNo("0226032690")
                .build();

        assertThatThrownBy(() -> service.reverseBill(request))
                .isInstanceOf(RestClientResponseException.class)
                .extracting("statusCode.value")
                .isEqualTo(409);
    }
}
