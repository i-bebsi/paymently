package com.uii.paymently.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RestTemplateConfigTest {

    @Autowired
    @Qualifier("paymentRestTemplate")
    private RestTemplate restTemplate;

    @Test
    void shouldCreateRestTemplateBean() {
        assertThat(restTemplate).isNotNull();
    }

    @Test
    void shouldUseHttpComponentsClientHttpRequestFactory() {
        // Verify that the factory is Apache HttpClient-based (bukan SimpleClientHttpRequestFactory)
        assertThat(restTemplate.getRequestFactory())
                .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }
}