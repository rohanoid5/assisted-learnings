package com.taskforge.taskforge.util;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class RequestIdGenerator {
    private final String id = UUID.randomUUID().toString();

    public String getId() { return id; }
}
