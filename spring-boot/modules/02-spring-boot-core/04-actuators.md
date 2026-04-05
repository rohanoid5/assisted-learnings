# 2.4 — Spring Boot Actuator

## Concept

**Spring Boot Actuator** adds production-ready operational endpoints to your application — health checks, metrics, environment info, thread dumps, and more. All you need is the `spring-boot-starter-actuator` dependency.

**Node.js analogy:** In Express you'd write custom `/health` and `/metrics` middleware. Popular libraries like `prom-client` add Prometheus metrics. Actuator is all of that — built in, configured in YAML, and standardized.

---

## Enabling Actuator

Just the dependency (you likely already have this):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

By default, only `/actuator/health` and `/actuator/info` are exposed over HTTP. Configure in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,loggers,threaddump,httptrace
        # Or expose everything (only in dev/internal environments):
        # include: "*"
      base-path: /actuator    # Default base path
  endpoint:
    health:
      show-details: when-authorized   # "always" in dev, "when-authorized" in prod
    info:
      enabled: true
  info:
    env:
      enabled: true           # Include application.yml 'info.*' properties
    build:
      enabled: true           # Include build info from META-INF/build-info.properties
```

---

## Core Endpoints Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Application health status |
| `/actuator/info` | GET | Application info (version, git commit, etc.) |
| `/actuator/metrics` | GET | List all metric names |
| `/actuator/metrics/{name}` | GET | Specific metric (e.g., `jvm.memory.used`) |
| `/actuator/env` | GET | All environment properties (⚠️ sensitive — don't expose publicly) |
| `/actuator/loggers` | GET/POST | View and change log levels at runtime |
| `/actuator/threaddump` | GET | Thread dump (useful for diagnosing hangs) |
| `/actuator/heapdump` | GET | JVM heap dump |
| `/actuator/beans` | GET | All Spring beans in the context |
| `/actuator/mappings` | GET | All URL mappings (`@RequestMapping` routes) |
| `/actuator/conditions` | GET | Autoconfiguration report |

**Try these:**
```bash
curl http://localhost:8080/actuator/health | jq
curl http://localhost:8080/actuator/metrics | jq
curl http://localhost:8080/actuator/metrics/http.server.requests | jq
curl http://localhost:8080/actuator/mappings | jq '.[].dispatcherServlets'
```

---

## Health Endpoint Deep Dive

The `/health` endpoint aggregates status from multiple **health indicators**:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 250790436864,
        "free": 112234491904,
        "threshold": 10485760
      }
    }
  }
}
```

Spring Boot auto-configures health indicators for common integrations: database (`DataSourceHealthIndicator`), disk space, Redis, MongoDB, Elasticsearch, etc. — automatically, when those are on the classpath.

**Health statuses:**
- `UP` — everything is fine
- `DOWN` — something is wrong
- `OUT_OF_SERVICE` — temporarily unavailable (maintenance mode)
- `UNKNOWN` — can't determine

---

## Custom Health Indicator

Write your own health indicator for any external dependency:

```java
package com.taskforge.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("taskforgeDatabase")    // Bean name → shows as "taskforgeDatabase" in response
public class DatabaseHealthIndicator implements HealthIndicator {

    private final UserRepository userRepository;

    public DatabaseHealthIndicator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Health health() {
        try {
            long userCount = userRepository.count();
            return Health.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("userCount", userCount)
                .withDetail("status", "Reachable")
                .build();
        } catch (Exception ex) {
            return Health.down()
                .withDetail("error", ex.getMessage())
                .withDetail("database", "PostgreSQL")
                .build();
        }
    }
}
```

---

## Customizing the `/info` Endpoint

```yaml
# application.yml
info:
  app:
    name: TaskForge
    description: Project Management API
    version: 1.0.0
  contact:
    team: Platform Engineering
    email: platform@taskforge.io
```

Add build info by configuring the Maven plugin in `pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <goals>
                        <goal>build-info</goal>   <!-- Generates build-info.properties -->
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Now `/actuator/info` returns:
```json
{
  "app": {
    "name": "TaskForge",
    "description": "Project Management API",
    "version": "1.0.0"
  },
  "build": {
    "artifact": "taskforge",
    "name": "taskforge",
    "time": "2026-03-27T10:00:00.000Z",
    "version": "0.0.1-SNAPSHOT",
    "group": "com.taskforge"
  }
}
```

---

## Changing Log Levels at Runtime

One of the most useful Actuator features — change log verbosity without restarting:

```bash
# View current log levels
curl http://localhost:8080/actuator/loggers/com.taskforge

# Change to DEBUG at runtime
curl -X POST http://localhost:8080/actuator/loggers/com.taskforge \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Reset to null (uses default)
curl -X POST http://localhost:8080/actuator/loggers/com.taskforge \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": null}'
```

---

## Securing Actuator Endpoints

In production, **never expose all actuator endpoints publicly**. Secure them:

```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info        # Only expose safe endpoints publicly
  endpoint:
    health:
      show-details: when-authorized # Only show details to authenticated users
```

Or require ADMIN role for actuator access (configured in your `SecurityConfig` in Module 5):
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/actuator/**").hasRole("ADMIN")
    .anyRequest().authenticated()
);
```

---

## Try It Yourself

**Exercise:** Add a custom health indicator to TaskForge.

1. Create `src/main/java/com/taskforge/health/AppHealthIndicator.java`:

```java
@Component("taskforge")
public class AppHealthIndicator implements HealthIndicator {

    private final AppProperties appProperties;

    public AppHealthIndicator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public Health health() {
        boolean jwtConfigured = appProperties.getJwt().getSecret() != null
            && !appProperties.getJwt().getSecret().isEmpty();

        if (jwtConfigured) {
            return Health.up()
                .withDetail("jwtConfigured", true)
                .withDetail("jwtExpirationMs", appProperties.getJwt().getExpirationMs())
                .build();
        } else {
            return Health.down()
                .withDetail("jwtConfigured", false)
                .withDetail("error", "JWT secret is not configured")
                .build();
        }
    }
}
```

2. Update `application.yml` to expose health details:
```yaml
management:
  endpoint:
    health:
      show-details: always
```

3. Call `curl http://localhost:8080/actuator/health | jq` and see the new `taskforge` component.

---

## Capstone Connection

TaskForge exposes three actuator endpoints in the `dev` profile: `health`, `info`, `metrics`. In production (`prod` profile), only `health` and `info` are exposed, with details requiring authentication. The custom `DatabaseHealthIndicator` is added in Module 4, once the database layer is in place.
