package com.taskforge.taskforge.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskforge.taskforge.service.HealthService;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "status", healthService.getStatus(),
            "uptimeSeconds", healthService.getUptimeSeconds()
        );
    }
}
