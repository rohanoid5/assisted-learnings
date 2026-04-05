# 06 — Circuit Breaker (Resilience4j)

## The Cascading Failure Problem

In microservices, a slow or failed downstream service can cascade:

```
Client → Project Service → Task Service (dead/slow)
              │
              └── Waits 30s for Task Service timeout
              └── All threads blocked
              └── Project Service also becomes unresponsive
              └── Gateway times out
              └── Client gets 503
```

Without protection, one failed service can bring down your entire system.

---

## Circuit Breaker Pattern

The circuit breaker sits between callers and services. It tracks failure rates and "opens" the circuit when failures exceed a threshold:

```
CLOSED (normal):      requests pass through
      │
      │ (failure rate > 50%)
      ▼
OPEN (tripped):       requests fail-fast (no waiting)
      │               fallback is returned immediately
      │ (after 30s wait window)
      ▼
HALF-OPEN (probing):  a few requests let through to test
      │
      ├── (success) → CLOSED
      └── (failure) → OPEN again
```

**Analogy (Node.js):** Like the `opossum` circuit breaker library — wraps async functions with failure rate monitoring.

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>  <!-- required for annotations -->
</dependency>
```

---

## @CircuitBreaker Annotation

```java
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final TaskServiceClient taskServiceClient;

    @CircuitBreaker(name = "task-service", fallbackMethod = "getTasksFallback")
    public List<TaskResponse> getTasksForProject(Long projectId) {
        return taskServiceClient.getTasksByProject(projectId, 0, 20).data().getContent();
    }

    // Fallback — same signature + Throwable parameter
    public List<TaskResponse> getTasksFallback(Long projectId, Throwable ex) {
        log.warn("Task service unavailable for project {}: {}", projectId, ex.getMessage());
        return Collections.emptyList();   // return safe default
    }
}
```

---

## Combined Annotations

```java
@CircuitBreaker(name = "task-service", fallbackMethod = "fallback")
@Retry(name = "task-service")
@TimeLimiter(name = "task-service")
@RateLimiter(name = "task-service")
public CompletableFuture<List<TaskResponse>> getTasksAsync(Long projectId) {
    return CompletableFuture.supplyAsync(() ->
        taskServiceClient.getTasksByProject(projectId, 0, 20).data().getContent()
    );
}
```

> `@TimeLimiter` requires the method to return `CompletableFuture`.

---

## Configuration

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      task-service:
        sliding-window-type: COUNT_BASED         # or TIME_BASED
        sliding-window-size: 10                  # last 10 calls
        failure-rate-threshold: 50               # open if >50% fail
        wait-duration-in-open-state: 30s         # wait 30s before HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s         # calls >2s count as slow
        slow-call-rate-threshold: 80             # open if >80% are slow
        register-health-indicator: true          # expose /actuator/health

  retry:
    instances:
      task-service:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - feign.FeignException.ServiceUnavailable
          - java.net.ConnectException

  timelimiter:
    instances:
      task-service:
        timeout-duration: 3s

  ratelimiter:
    instances:
      task-service:
        limit-for-period: 20        # 20 calls per refresh period
        limit-refresh-period: 1s
        timeout-duration: 100ms     # wait up to 100ms for a permit
```

---

## Feign + Circuit Breaker

In the Gateway, you already saw CircuitBreaker in route filter. With Feign, enable it:

```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true          # wraps all Feign calls with circuit breaker
```

Then add fallback class:

```java
@FeignClient(name = "task-service", fallback = TaskServiceClientFallback.class)
public interface TaskServiceClient {
    @GetMapping("/api/tasks")
    ApiResponse<Page<TaskResponse>> getTasksByProject(@RequestParam Long projectId, ...);
}

@Component
class TaskServiceClientFallback implements TaskServiceClient {
    
    @Override
    public ApiResponse<Page<TaskResponse>> getTasksByProject(Long projectId, ...) {
        return ApiResponse.error("Task service unavailable");
    }
}
```

---

## Actuator Endpoints

After enabling, circuit breaker state is visible:

```bash
# Health — shows OPEN/CLOSED/HALF_OPEN
curl http://localhost:8082/actuator/health

# Metrics
curl http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state
curl http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.calls
```

---

## Next

[07 — Micrometer and Distributed Tracing](./07-micrometer.md) — observing what's happening across services.
