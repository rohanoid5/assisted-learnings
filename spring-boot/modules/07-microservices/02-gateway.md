# 02 — Spring Cloud Gateway

## What Is an API Gateway?

An API Gateway is a **single entry point** in front of all your microservices. Instead of clients calling each service directly, they call the gateway, which routes the request to the right service.

```
Client → API Gateway (port 8080)
              │
    ┌─────────┼─────────────────┐
    │         │                 │
    ▼         ▼                 ▼
Auth Service  Project Service   Task Service
 :8081         :8082              :8083
```

**Cross-cutting concerns** the gateway handles centrally:
- Authentication (JWT validation)
- Rate limiting
- Request logging
- CORS
- SSL termination
- Response caching

**Analogy (Node.js):** Like an nginx reverse proxy configured with `proxy_pass`, but with programmable routing, filters, and predicates in Java/YAML.

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

> **Note:** Spring Cloud Gateway uses WebFlux (reactive) under the hood. It's a separate application — not part of your monolith.

---

## Route Configuration in YAML

```yaml
# gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        # Auth Service
        - id: auth-service
          uri: http://localhost:8081   # or lb://auth-service with Eureka
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=0           # don't strip path prefix

        # Project Service
        - id: project-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/projects/**

        # Task Service
        - id: task-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/tasks/**,/api/tasks/{id}/comments/**

      # Global filters applied to ALL routes
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin

server:
  port: 8080
```

---

## Route Configuration in Code

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

            .route("auth-service", r -> r
                .path("/api/auth/**")
                .uri("http://localhost:8081")
            )

            .route("project-service", r -> r
                .path("/api/projects/**")
                .filters(f -> f
                    .addRequestHeader("X-Internal-Request", "gateway")  // pass metadata
                    .retry(config -> config.setRetries(3))
                )
                .uri("http://localhost:8082")
            )

            .route("task-service", r -> r
                .path("/api/tasks/**")
                .filters(f -> f
                    .circuitBreaker(config -> config
                        .setName("task-service-cb")
                        .setFallbackUri("forward:/fallback/tasks"))
                )
                .uri("http://localhost:8083")
            )

            .build();
    }
}
```

---

## Built-in Filters

```yaml
filters:
  - AddRequestHeader=X-Request-Source, gateway  # add header to upstream request
  - AddResponseHeader=X-Gateway-Version, 1.0    # add header to response
  - RewritePath=/api/v1/(?<segment>.*), /api/${segment}  # rewrite URL
  - StripPrefix=1                                # strip first path segment
  - PrefixPath=/api                              # prepend path prefix
  - RequestRateLimiter=...                       # rate limiting (requires Redis)
  - Retry=3                                      # retry on 5xx
  - CircuitBreaker=...                           # circuit breaker
```

---

## JWT Authentication Filter

Validate JWTs at the gateway so downstream services don't have to:

```java
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;

    // Public paths — skip JWT validation
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/register",
        "/api/auth/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            return unauthorized(exchange);
        }

        // Optionally forward userId to downstream services
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-User-Id", String.valueOf(userId))
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;   // run before other filters
    }
}
```

---

## Rate Limiting

Rate limiting with Redis:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10     # 10 requests/second
                redis-rate-limiter.burstCapacity: 20     # allow burst of 20
                key-resolver: "#{@ipKeyResolver}"        # rate-limit per IP
```

```java
@Bean
KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
    );
}
```

---

## CORS at the Gateway

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "http://localhost:3000"
            allowed-methods: "GET,POST,PUT,PATCH,DELETE,OPTIONS"
            allowed-headers: "Authorization,Content-Type"
            allow-credentials: true
            max-age: 3600
```

Configure CORS at the gateway once instead of in each service.

---

## Fallback Endpoints

When a service is down, return a graceful response:

```java
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Void>> tasksFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error("Task service is temporarily unavailable. Please try again."));
    }
}
```

---

## Next

[03 — Spring Cloud Config](./03-cloud-config.md) — centralized configuration management.
