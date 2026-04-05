# 04 — Eureka Service Discovery

## The Problem

In microservices, services need to call each other. If you hardcode `http://task-service:8083`, you have a problem when:
- Task service moves to a different host
- Multiple instances start on different ports (auto-scaling)
- Kubernetes assigns dynamic IPs

**Service Discovery:** Services register themselves in a registry. Other services query the registry to find where to call.

```
1. Task Service starts → registers with Eureka
   "I'm 'task-service' at 10.0.1.5:8083"

2. Project Service wants to call Task Service:
   "Eureka, where is 'task-service'?"
   Eureka: "10.0.1.5:8083 (healthy)"

3. Project Service calls 10.0.1.5:8083 directly
```

---

## Eureka Server Setup

**New dedicated application:**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# eureka-server/src/main/resources/application.yml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false    # don't self-register
    fetch-registry: false          # don't fetch own registry
  server:
    enable-self-preservation: false    # disable for dev (avoids stale registrations)
```

**Eureka Dashboard:** http://localhost:8761 — visual list of all registered services.

---

## Eureka Client Setup

Every microservice that wants to register:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

```yaml
# task-service/src/main/resources/application.yml
spring:
  application:
    name: task-service    # name used in Eureka registry

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true    # register with IP (not hostname)
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

The `@EnableDiscoveryClient` annotation is optional since Spring Cloud 2020+ (auto-configured).

---

## Using lb:// in Gateway Routes

Once services are registered in Eureka, replace hardcoded URLs with logical names:

```yaml
# gateway/application.yml — BEFORE
spring:
  cloud:
    gateway:
      routes:
        - id: task-service
          uri: http://localhost:8083   # brittle — breaks on port change
          predicates:
            - Path=/api/tasks/**

# gateway/application.yml — AFTER (with Eureka)
spring:
  cloud:
    gateway:
      routes:
        - id: task-service
          uri: lb://task-service       # lb = load balanced, name from Eureka
          predicates:
            - Path=/api/tasks/**
```

The `lb://` prefix tells Spring Cloud Gateway to look up `task-service` in Eureka and load-balance across all healthy instances.

---

## Health Checks

Eureka relies on heartbeats (default: 30 seconds) to detect dead services. Configure Actuator health endpoint for Eureka integration:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always

eureka:
  instance:
    health-check-url-path: /actuator/health
    status-page-url-path: /actuator/info
```

---

## High Availability

In production, run multiple Eureka instances and have them replicate:

```yaml
# eureka-server-1
server.port: 8761
eureka:
  client:
    service-url:
      defaultZone: http://eureka-2:8762/eureka/

# eureka-server-2
server.port: 8762
eureka:
  client:
    service-url:
      defaultZone: http://eureka-1:8761/eureka/
```

Each client registers with both:
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-1:8761/eureka/,http://eureka-2:8762/eureka/
```

---

## Kubernetes Alternative

In Kubernetes, you typically skip Eureka and use Kubernetes-native service discovery:
- `ClusterIP` services handle load balancing
- Pods reach each other via `http://service-name:8080`
- Health checks via readiness/liveness probes

Spring Cloud Kubernetes provides integration with Kubernetes Config Maps and Secrets as an alternative to Spring Cloud Config + Eureka.

---

## Next

[05 — OpenFeign](./05-open-feign.md) — declarative HTTP clients for service-to-service calls.
