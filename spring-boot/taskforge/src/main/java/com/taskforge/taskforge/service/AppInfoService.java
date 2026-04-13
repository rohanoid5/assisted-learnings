package com.taskforge.taskforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.taskforge.taskforge.config.AppProperties;

import jakarta.annotation.PostConstruct;

@Service
public class AppInfoService {
    private static final Logger log = LoggerFactory.getLogger(AppInfoService.class);
    private final AppProperties appProperties;

    public AppInfoService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void printConfig() {
        log.info("JWT expiration: {} ms", appProperties.getJwt().getExpirationMs());
        // Never log the actual secret!
        log.info("JWT secret configured: {}", appProperties.getJwt().getSecret() != null);
    }
}
