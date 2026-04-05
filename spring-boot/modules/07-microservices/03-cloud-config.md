# 03 — Spring Cloud Config

## The Problem

In a microservices system, you have many services, each with their own `application.yml`. When you need to change a shared property (e.g., JWT secret, DB connection pool size), you'd have to update and redeploy every service.

**Spring Cloud Config** provides a centralized configuration server backed by a Git repository.

```
Git Repo (config source)
    │
    ▼
Config Server (port 8888)
    │
    ├──► Auth Service      reads its config at startup
    ├──► Project Service   reads its config at startup
    └──► Task Service      reads its config at startup
```

---

## Config Server Setup

**New Spring Boot application — config server:**

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# config-server/src/main/resources/application.yml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/taskforge-config    # Git repo with configs
          clone-on-start: true
          default-label: main    # Git branch
```

---

## Config Repository Structure

```
taskforge-config/          (Git repository)
├── application.yml        (shared by ALL services)
├── auth-service.yml       (auth-service specific)
├── project-service.yml    (project-service specific)
├── task-service.yml       (task-service specific)
└── application-prod.yml   (production overrides for all)
```

**application.yml (shared):**
```yaml
app:
  jwt:
    secret: ${JWT_SECRET}         # injected from env var on server
    expiration-ms: 86400000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

**auth-service.yml (service-specific):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://auth-db:5432/taskforge_auth
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## Config Client Setup

Each microservice adds the config client:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

```yaml
# auth-service/src/main/resources/application.yml
spring:
  application:
    name: auth-service           # matches auth-service.yml in config repo
  config:
    import: "configserver:http://localhost:8888"  # or use Eureka to find it
```

At startup, the service fetches its config from the config server before starting.

---

## Refresh Without Restart

When config changes, services can pick up new values without restarting:

**Step 1:** Annotate beans with `@RefreshScope`:
```java
@Component
@RefreshScope    // re-injected on /actuator/refresh
public class JwtTokenProvider {
    @Value("${app.jwt.secret}")
    private String secret;
    // ...
}
```

**Step 2:** Trigger refresh via actuator:
```bash
curl -X POST http://localhost:8081/actuator/refresh
```

**Step 3:** For all services at once — use Spring Cloud Bus (with Kafka or RabbitMQ):
```bash
curl -X POST http://localhost:8888/actuator/busrefresh
# → broadcasts refresh to all subscribed services
```

---

## Config Priority Order

From lowest to highest priority:

```
1. Shared application.yml (from config repo)
2. Service-specific {name}.yml (from config repo)  
3. Profile-specific application-{profile}.yml (from config repo)
4. Local application.yml (service's own resources)
5. Environment variables
6. Command-line arguments
```

Higher priority overrides lower. Environment variables always win over config files.

---

## Local Development Override

During development, you can skip the config server:

```yaml
# application-dev.yml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"  # prefix with optional:
```

The `optional:` prefix prevents startup failure if the config server isn't running.

---

## Next

[04 — Eureka Service Discovery](./04-eureka.md) — services finding each other dynamically.
