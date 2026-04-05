# 07 — Micrometer and Distributed Tracing

## Why Observability Matters

In a microservices system, a single user request might touch 5 services. When something goes wrong:
- Which service is slow?
- Where did the error originate?
- How long did each step take?

**Observability = Metrics + Logs + Traces**

---

## The Three Pillars

| Pillar | What | Tool |
|--------|------|------|
| **Metrics** | Aggregated numbers over time (request rate, error rate, latency) | Micrometer → Prometheus → Grafana |
| **Logs** | Timestamped events with context | Logback → ELK Stack |
| **Traces** | Full journey of a single request across services | Micrometer Tracing → Zipkin |

---

## Micrometer — Metrics

**Setup:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<!-- Export metrics in Prometheus format -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}   # tag all metrics with service name
```

**Access metrics:**
```bash
curl http://localhost:8080/actuator/prometheus
# Returns Prometheus-format metrics:
# http_server_requests_seconds_count{method="GET",status="200",uri="/api/tasks"}  42.0
# http_server_requests_seconds_sum{...}  0.453
# jvm_memory_used_bytes{area="heap",...}  ...
```

---

## Custom Metrics

```java
@Service
@RequiredArgsConstructor
public class TaskService {

    private final MeterRegistry meterRegistry;

    // Counter — tracks how many times something happened
    private final Counter tasksCreatedCounter;
    private final Counter tasksCompletedCounter;

    // Record in Timer for latency
    private final Timer taskCreationTimer;

    @PostConstruct
    void initMetrics() {
        Counter.builder("tasks.created")
            .description("Number of tasks created")
            .tag("service", "task-service")
            .register(meterRegistry);
    }

    public TaskResponse createTask(CreateTaskRequest request, Long userId) {
        return taskCreationTimer.record(() -> {
            Task task = buildTask(request, userId);
            Task saved = taskRepository.save(task);
            meterRegistry.counter("tasks.created").increment();
            return toResponse(saved);
        });
    }

    public void completeTask(Long taskId) {
        // ...
        meterRegistry.counter("tasks.completed",
            "priority", task.getPriority().name()   // dimensions/tags
        ).increment();
    }
}
```

---

## Distributed Tracing with Micrometer Tracing

**Setup (Micrometer + Zipkin):**
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

```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 1.0 = 100% of requests traced (use 0.1 in prod)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**That's it.** Spring Boot auto-instruments:
- HTTP requests (in/out)
- Feign client calls
- Database queries
- `@Async` tasks

---

## What Tracing Shows

```
Trace ID: abc123

[Project Service      ] GET /api/projects/1/details   150ms
  [Task Service Feign ] GET /api/tasks?projectId=1     120ms
    [Task Service DB  ] SELECT * FROM tasks WHERE...     8ms
    [Task Service DB  ] SELECT * FROM users WHERE id=..  3ms
  [User Service Feign ] GET /api/users/42               20ms
    [User Service DB  ] SELECT * FROM users WHERE id=..  2ms
```

All services emit spans with the same `traceId`. Zipkin stitches them into a waterfall view.

---

## Starting Zipkin Locally

```bash
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin

# Dashboard
open http://localhost:9411
```

---

## Structured Logging with Trace IDs

Micrometer Tracing automatically injects `traceId` and `spanId` into MDC (Mapped Diagnostic Context):

```yaml
# logback-spring.xml or via application.yml
logging:
  pattern:
    console: "%d{HH:mm:ss} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{36} - %msg%n"
```

Now log output looks like:
```
14:32:01 [http-nio-8083] INFO  [abc123def456,abc123def456] c.t.service.TaskService - Creating task: "Fix bug"
14:32:01 [http-nio-8083] DEBUG [abc123def456,zyx789]       c.t.repository.TaskRepo  - Executing query...
```

The `traceId` `abc123def456` lets you correlate all logs for one user request across all services.

---

## Prometheus + Grafana Stack

```yaml
# docker-compose.yml (observability stack)
services:
  prometheus:
    image: prom/prometheus
    ports: ["9090:9090"]
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'task-service'
    static_configs:
      - targets: ['task-service:8083']
    metrics_path: /actuator/prometheus
    scrape_interval: 15s

  - job_name: 'project-service'
    static_configs:
      - targets: ['project-service:8082']
    metrics_path: /actuator/prometheus
```

Grafana queries Prometheus to build dashboards: request rate per endpoint, error rates, JVM memory, circuit breaker state.

---

## Module 7 Exercises

See [exercises/README.md](./exercises/README.md) for hands-on exercises.
