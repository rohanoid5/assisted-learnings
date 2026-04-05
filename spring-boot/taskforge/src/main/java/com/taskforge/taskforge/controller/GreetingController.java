package com.taskforge.taskforge.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskforge.taskforge.service.GreetingService;

@RestController
@RequestMapping("/api/greeting")
public class GreetingController {
    public final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @RequestMapping
    public String getGreeting() {
        return greetingService.getGreeting();
    }
}
