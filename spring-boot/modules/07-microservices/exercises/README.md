# Module 7 Exercises — Microservices

These exercises are conceptual and configuration-based. TaskForge is a monolith, so you'll extend it with microservices patterns rather than splitting it into separate projects.

---

## Exercise 1 — Add Actuator and Custom Metrics to TaskForge

**Goal:** Expose metrics from the TaskForge monolith via Micrometer.

**Step 1:** Ensure these dependencies are in pom.xml:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Step 2:** Configure in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: taskforge
```

**Step 3:** Add a custom counter to `TaskService.createTask()`:
```java
@Autowired MeterRegistry meterRegistry;

// In createTask():
meterRegistry.counter("tasks.created",
    "priority", request.priority().name()
).increment();
```

**Verify:**
```bash
./mvnw spring-boot:run

# Health check
curl http://localhost:8080/actuator/health | jq .

# All metrics
curl http://localhost:8080/actuator/metrics | jq .names

# Tasks created metric (create a few tasks first via API)
curl http://localhost:8080/actuator/metrics/tasks.created | jq .

# Prometheus format
curl http://localhost:8080/actuator/prometheus | grep tasks_
```

---

## Exercise 2 — Distributed Tracing with Zipkin

**Goal:** Add distributed tracing to visualize request flows.

**Step 1:** Add tracing dependencies:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**Step 2:** Configure:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # trace everything in dev

logging:
  pattern:
    console: "%d{HH:mm:ss} [%thread] %-5level [%X{traceId}] %logger{25} - %msg%n"
```

**Step 3:** Start Zipkin:
```bash
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin
```

**Step 4:** Make a few API calls:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -d '{"email":"alice@test.com","password":"Password1!"}' \
  -H "Content-Type: application/json" | jq -r '.data.accessToken')

curl -s "http://localhost:8080/api/tasks?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

**Verify:** Open http://localhost:9411 → Find Traces → select `taskforge` → see trace waterfall with DB spans.

---

## Exercise 3 — Gateway Design Exercise (Architecture)

**No code required — think through the design.**

If you were to extract TaskForge's Auth module into a separate service:

1. **What would the API Gateway route look like?**
   - Write the YAML `spring.cloud.gateway.routes` entry for `/api/auth/**`

2. **What information does the gateway need to validate JWTs?**
   - Should the gateway call Auth Service for every request, or validate locally?
   - What are the tradeoffs?

3. **How would Project Service know who the current user is after the gateway validates the JWT?**
   - Hint: `X-User-Id` request header

4. **Sketch the request flow as an ASCII diagram:**
   ```
   Client → [?] → [?] → [?]
   ```

---

## Exercise 4 — Resilience: Add Retry to Feign

**Goal:** If TaskForge called an external service (e.g., a notification service), add retry logic.

**Step 1:** Add `spring-cloud-starter-openfeign` and `spring-cloud-starter-circuitbreaker-resilience4j` to pom.xml.

**Step 2:** Create a stub Feign client for a hypothetical notification service:

```java
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://localhost:9090}",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/task-assigned")
    void notifyTaskAssigned(@RequestBody TaskAssignedEvent event);
}

@Component
class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void notifyTaskAssigned(TaskAssignedEvent event) {
        log.warn("Notification service unavailable, skipping notification for task {}",
            event.taskId());
    }
}
```

**Step 3:** Configure retry in `application.yml`:
```yaml
resilience4j:
  retry:
    instances:
      notification-service:
        max-attempts: 3
        wait-duration: 200ms

spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
```

**Step 4:** Verify the fallback fires by pointing the URL to a nonexistent server.

---

## Exercise 5 — Architecture Review

Review the TaskForge capstone at the end of the tutorial and answer:

1. **Which endpoints are the highest-read traffic?** (List endpoints + justification)
2. **If you had to extract ONE microservice from TaskForge, which would you choose and why?**
3. **What would you use instead of in-memory caching at scale?** (Hint: Redis `@Cacheable`)
4. **How would you implement audit logging across services?** (Hint: Event sourcing with Kafka)
5. **What's the biggest risk of splitting to microservices for a small team?**

---

## Module 7 Capstone Checkpoint

```
Micrometer + Actuator:
[ ] /actuator/health returns UP with detail
[ ] /actuator/prometheus returns metrics
[ ] Custom tasks.created counter increments when creating tasks

Tracing:
[ ] Trace IDs appear in log output
[ ] Zipkin UI shows traces for API calls
[ ] DB spans visible within HTTP spans

Gateway design:
[ ] Routing YAML written for at least 2 routes
[ ] JWT validation strategy documented
[ ] X-User-Id propagation pattern understood
```

---

## Up Next

[Module 8 — Capstone Integration](../../08-capstone-integration/README.md), then build the full TaskForge source code in `capstone/taskforge/`.
