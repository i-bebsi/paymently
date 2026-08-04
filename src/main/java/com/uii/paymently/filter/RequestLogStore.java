package com.uii.paymently.filter;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RequestLogStore {

    private final Deque<RequestLogEntry> entries = new ConcurrentLinkedDeque<>();

    public void add(RequestLogEntry entry) {
        entries.addFirst(entry);
    }

    public List<RequestLogEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public List<RequestLogEntry> getRecent(int n) {
        return entries.stream().limit(n).toList();
    }

    @Data
    @Builder
    public static class RequestLogEntry {
        private Instant timestamp;
        private String clientIp;
        private String method;
        private String uri;
        private String upstreamUrl;
        private Map<String, String> requestHeaders;
        private String requestBody;
        private int responseStatus;
        private Map<String, String> responseHeaders;
        private String responseBody;
        private long durationMs;
    }
}
