package com.uii.paymently.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class RequestLogStore {

    private static final Path LOG_FILE = Paths.get("logs", "request-log.json");
    private final Deque<RequestLogEntry> entries = new ConcurrentLinkedDeque<>();
    private final ObjectMapper objectMapper;

    public RequestLogStore() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    void loadFromFile() {
        if (!Files.exists(LOG_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    RequestLogEntry entry = objectMapper.readValue(line, RequestLogEntry.class);
                    entries.addLast(entry);
                } catch (Exception e) {
                    log.debug("Gagal parse request log line: {}", e.getMessage());
                }
            }
            log.info("Loaded {} request log entries from {}", entries.size(), LOG_FILE);
        } catch (IOException e) {
            log.warn("Gagal membaca request log file: {}", e.getMessage());
        }
    }

    public void add(RequestLogEntry entry) {
        entries.addFirst(entry);
        appendToFile(entry);
    }

    private void appendToFile(RequestLogEntry entry) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            String json = objectMapper.writeValueAsString(entry);
            Files.writeString(LOG_FILE, json + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.debug("Gagal menulis request log ke file: {}", e.getMessage());
        }
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
