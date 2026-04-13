# Module 1 — Exercises

## Overview

Complete all exercises before moving to Module 2. They reinforce the concepts from all 9 topic files and build the initial TaskForge project scaffold.

---

## Exercise 1: Spring Initializr — Generate TaskForge

**Goal:** Create the TaskForge project skeleton.

1. Open [https://start.spring.io](https://start.spring.io)
2. Configure:
   - **Project:** Maven
   - **Language:** Java
   - **Spring Boot:** 3.2.x (latest stable 3.x)
   - **Group:** `com.taskforge`
   - **Artifact:** `taskforge`
   - **Name:** `taskforge`
   - **Description:** `Project management API — Spring Boot learning project`
   - **Package name:** `com.taskforge`
   - **Packaging:** Jar
   - **Java:** 17

3. Add these dependencies:
   - `Spring Web`
   - `Spring Boot Actuator`
   - `Spring Boot DevTools` (hot reload in dev)
   - `Lombok`

4. Click **Generate**, unzip, and move to `capstone/taskforge/`

5. Open in IntelliJ IDEA:
   - File → Open → select `capstone/taskforge/pom.xml` → Open as Project
   - Wait for Maven to download dependencies

6. Run the app: `mvn spring-boot:run`
   - You should see the Spring Boot banner and `Started TaskforgeApplication`

**Verification:** `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

---

## Exercise 2: Configuration Profiles

**Goal:** Set up dev, prod, and test configuration profiles.

1. Replace `src/main/resources/application.properties` with `application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: taskforge
  profiles:
    active: dev    # Default active profile

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  info:
    env:
      enabled: true

info:
  app:
    name: TaskForge
    description: Project Management API
    version: 1.0.0
```

2. Create `application-dev.yml`:

```yaml
spring:
  devtools:
    restart:
      enabled: true

app:
  jwt:
    secret: dev-secret-key-this-is-at-least-256-bits-long-for-hs256-algorithm
    expiration-ms: 86400000  # 24 hours

logging:
  level:
    com.taskforge: DEBUG
```

3. Create `application-prod.yml`:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 86400000

logging:
  level:
    root: WARN
    com.taskforge: INFO
```

4. Create `application-test.yml` (used by `@SpringBootTest`):

```yaml
app:
  jwt:
    secret: test-secret-key-this-is-at-least-256-bits-long-for-hs256-algorithm
    expiration-ms: 3600000   # 1 hour for tests
```

5. Run with `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`

**Verification:** Call `/actuator/info` and see the app info in the response.

---

## Exercise 3: @ConfigurationProperties

**Goal:** Create type-safe config classes for TaskForge.

1. Create `src/main/java/com/taskforge/config/AppProperties.java`:

```java
package com.taskforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {
        private String secret;
        private long expirationMs;

        // Getters and setters (required for binding)
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    }
}
```

2. Create a simple `@Service` that injects and logs `AppProperties`:

```java
package com.taskforge.service;

import com.taskforge.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
```

**Verification:** See the log output in the console after startup.

---

## Exercise 4: Dependency Injection + Layered Architecture

**Goal:** Create the first proper service layer with constructor injection.

1. Create the package structure:
```
src/main/java/com/taskforge/
├── controller/
├── service/
├── repository/
├── model/
├── dto/
│   ├── request/
│   └── response/
├── config/
└── exception/
```

2. Create a simple `HealthService` that demonstrates DI:

```java
package com.taskforge.service;

import org.springframework.stereotype.Service;
import java.time.Instant;

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
```

3. Create a `HealthController`:

```java
package com.taskforge.controller;

import com.taskforge.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

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
```

**Verification:** `curl http://localhost:8080/api/status`
```json
{"status":"UP","uptimeSeconds":42}
```

---

## Exercise 5: AOP Logging Aspect

**Goal:** Add a logging aspect that logs all service method calls.

1. Add to `pom.xml` inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

2. Create `src/main/java/com/taskforge/aspect/ServiceLoggingAspect.java`:

```java
package com.taskforge.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Pointcut("execution(* com.taskforge.service.*.*(..))")
    public void serviceLayer() {}

    @Around("serviceLayer()")
    public Object logAndTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        log.debug("→ {}", method);
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.debug("← {} ({}ms)", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception ex) {
            log.error("✗ {} threw: {}", method, ex.getMessage());
            throw ex;
        }
    }
}
```

3. Call `GET /api/status` and check DEBUG logs.

**Verification:** You should see:
```
DEBUG → HealthService.getStatus()
DEBUG ← HealthService.getStatus() (0ms)
DEBUG → HealthService.getUptimeSeconds()
DEBUG ← HealthService.getUptimeSeconds() (0ms)
```

---

## Module 1 Capstone Checkpoint

By now TaskForge should have:
- [x] Spring Boot app starting cleanly
- [x] Three config profiles: dev, prod, test
- [x] `AppProperties` with type-safe JWT config binding
- [x] `HealthController` → `HealthService` wired via constructor injection
- [x] `ServiceLoggingAspect` logging all service calls
- [x] AOP starter in `pom.xml`

If all items are checked, proceed to [Module 2 — Spring Boot Core](../../02-spring-boot-core/README.md).
