package com.taskforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.taskforge.config.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TaskforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskforgeApplication.class, args);
    }
}
