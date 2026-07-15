package com.uii.paymently.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${payment.api.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${payment.api.read-timeout:20s}")
    private Duration readTimeout;

    @Bean
    public RestTemplate paymentRestTemplate(RestTemplateBuilder builder) {
        // Apache HttpClient 5 connection manager
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);
        connectionManager.setDefaultMaxPerRoute(10);

        // Interceptor untuk logging request — log header & URL sebelum request dikirim
        HttpRequestInterceptor requestInterceptor = (HttpRequest request, EntityDetails entity, HttpContext context) -> {
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
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setConnectionRequestTimeout((int) connectTimeout.toMillis());

        RestTemplate restTemplate = builder
                .requestFactory(() -> factory)
                .setReadTimeout(readTimeout)
                .build();

        return restTemplate;
    }
}