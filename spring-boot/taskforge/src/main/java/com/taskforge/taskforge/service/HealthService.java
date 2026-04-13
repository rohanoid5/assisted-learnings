package com.taskforge.taskforge.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final Instant startTime = Instant.now();

    public String getStatus() {
        return "UP";
    }

    public long getUptimeSeconds() {
        return Instant.now().getEpochSecond() - startTime.getEpochSecond();
    }
}
