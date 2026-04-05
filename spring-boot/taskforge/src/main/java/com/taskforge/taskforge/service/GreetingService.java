package com.taskforge.taskforge.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taskforge.taskforge.util.RequestIdGenerator;

import jakarta.annotation.PostConstruct;

@Service
public class GreetingService {
    private final RequestIdGenerator requestIdGenerator;


    @Autowired
    public GreetingService(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    public String getGreeting() {
        return "Hello, welcome to TaskForge!" + " Your request ID is: " + requestIdGenerator.getId();
    }

    @PostConstruct
    public void init() {
        System.out.println("GreetingService initialized.");
    }
}
