# Module 2 — Exercises

## Overview

These exercises reinforce starters, autoconfiguration, embedded server, and Actuator — and add operational visibility to the TaskForge project.

---

## Exercise 1: Explore Starters

**Goal:** Understand what's included in your project.

1. Run the dependency tree:
```bash
cd capstone/taskforge
mvn dependency:tree | grep -E "spring-boot-starter|tomcat|jackson"
```

2. Answer these questions:
   - Which version of Tomcat is embedded?
   - Which version of Jackson is included?
   - What does `spring-boot-starter-test` include?

<details>
<summary>Commands to find answers</summary>

```bash
mvn dependency:tree | grep tomcat-embed-core
mvn dependency:tree | grep jackson-databind
mvn dependency:tree | grep "starter-test" -A 10
```
</details>

---

## Exercise 2: Enable and Explore Actuator

**Goal:** Configure all useful Actuator endpoints.

1. Update `application-dev.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,beans,mappings,conditions,loggers
  endpoint:
    health:
      show-details: always
  info:
    env:
      enabled: true

info:
  app:
    name: TaskForge
    description: Project Management API
    version: 1.0.0
```

2. Add `build-info` goal to the Maven plugin in `pom.xml` (see `04-actuators.md` for the XML).

3. Rebuild and start: `mvn clean spring-boot:run`

4. Explore each endpoint:
```bash
curl http://localhost:8080/actuator/health | jq
curl http://localhost:8080/actuator/info | jq
curl http://localhost:8080/actuator/metrics | jq '.names[]' | head -20
curl http://localhost:8080/actuator/beans | jq 'keys' | head -10
curl http://localhost:8080/actuator/mappings | jq '.' | head -40
```

**Verification:** The `/actuator/info` response should include the `build` section with build time.

---

## Exercise 3: Custom Health Indicator

**Goal:** Build and test a custom `HealthIndicator`.

1. Create `src/main/java/com/taskforge/health/AppHealthIndicator.java`:

```java
package com.taskforge.health;

import com.taskforge.config.AppProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("taskforgeConfig")
public class AppHealthIndicator implements HealthIndicator {

    private final AppProperties appProperties;

    public AppHealthIndicator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public Health health() {
        boolean jwtConfigured = appProperties.getJwt().getSecret() != null
                && appProperties.getJwt().getSecret().length() >= 32;

        if (!jwtConfigured) {
            return Health.down()
                    .withDetail("error", "JWT secret is missing or too short (minimum 32 chars)")
                    .build();
        }

        return Health.up()
                .withDetail("jwtConfigured", true)
                .withDetail("jwtExpirationDays", appProperties.getJwt().getExpirationMs() / 86400000)
                .build();
    }
}
```

2. Call `curl http://localhost:8080/actuator/health | jq` and verify:
```json
{
  "status": "UP",
  "components": {
    "taskforgeConfig": {
      "status": "UP",
      "details": {
        "jwtConfigured": true,
        "jwtExpirationDays": 1
      }
    }
  }
}
```

3. Test the DOWN path: temporarily set an invalid `app.jwt.secret` (less than 32 chars) in `application-dev.yml` and restart. Verify the health check reports `DOWN`.

---

## Exercise 4: Runtime Log Level Change

**Goal:** Use Actuator to change log levels without restarting.

1. Check current log level:
```bash
curl http://localhost:8080/actuator/loggers/com.taskforge | jq
```

2. Change it to TRACE at runtime:
```bash
curl -X POST http://localhost:8080/actuator/loggers/com.taskforge \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "TRACE"}'
```

3. Call `GET /api/status` and observe the extra log output.

4. Reset to INFO:
```bash
curl -X POST http://localhost:8080/actuator/loggers/com.taskforge \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "INFO"}'
```

---

## Exercise 5: Autoconfiguration Report

**Goal:** Understand what's been autoconfigured.

1. Add to `application-dev.yml`:
```yaml
logging:
  level:
    org.springframework.boot.autoconfigure: DEBUG
```

2. Restart and scroll through the `AUTO-CONFIGURATION REPORT`.

3. Find and note:
   - Does `DataSourceAutoConfiguration` match or not? Why?
   - Does `SecurityAutoConfiguration` match? What condition is it checking?
   - Does `MongoAutoConfiguration` match? Why not?

<details>
<summary>Expected answers</summary>

- `DataSourceAutoConfiguration`: **Does NOT match** (negative) because you haven't added `spring-boot-starter-data-jpa` yet — `DataSource` class isn't on the classpath in a meaningful way.
- `SecurityAutoConfiguration`: Depends on whether you added `spring-boot-starter-security`. If not, it won't match.
- `MongoAutoConfiguration`: **Does NOT match** — `com.mongodb.MongoClient` is not on the classpath.
</details>

---

## Module 2 Capstone Checkpoint

By now TaskForge should have:
- [ ] All starters documented and understood (`pom.xml`)
- [ ] Actuator configured with `health`, `info`, `metrics` endpoints
- [ ] `/actuator/info` returning app name, description, version, and build time
- [ ] Custom `AppHealthIndicator` reporting JWT configuration status
- [ ] Able to change log levels at runtime via Actuator

If all items are checked, proceed to [Module 3 — Spring MVC](../../03-spring-mvc/README.md).
