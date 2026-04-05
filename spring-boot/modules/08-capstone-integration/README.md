# Module 8: Capstone Integration

## Overview

You've built TaskForge feature by feature across 6 modules (7 if you did microservices). This final module focuses on **production readiness** — tying everything together, containerizing the application, and preparing it for deployment.

---

## Learning Objectives

- [ ] Write a production-ready `application-prod.yml` configuration
- [ ] Containerize TaskForge with a multi-stage **Dockerfile**
- [ ] Orchestrate the full stack (app + PostgreSQL) with **Docker Compose**
- [ ] Understand important production concerns: connection pooling, logging, graceful shutdown
- [ ] Run a final end-to-end validation

---

## Final Architecture

```
┌─────────────────────────────────────────────┐
│              Docker Compose                  │
│                                              │
│  ┌─────────────────┐  ┌──────────────────┐  │
│  │  taskforge-api  │  │   postgres:15    │  │
│  │   (port 8080)   │──│  taskforge_prod  │  │
│  │  Spring Boot    │  │  (port 5432)     │  │
│  └─────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## Step 1: Production Configuration

Create `src/main/resources/application-prod.yml`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate          # Never auto-create in production
    show-sql: false

server:
  port: 8080
  shutdown: graceful              # Wait for in-flight requests to complete

app:
  jwt:
    secret: ${JWT_SECRET}         # Must be injected via environment variable
    expiration-ms: 86400000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: WARN
    com.taskforge: INFO
```

**Key rule:** Never hardcode secrets. Use environment variables (`${VAR_NAME}`) and inject them at runtime. This is the same rule as in Node.js — `.env` for local, secrets manager for production.

---

## Step 2: Dockerfile

Create `Dockerfile` in the project root (`capstone/taskforge/`):

```dockerfile
# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml first (layer caching - like Docker layer caching for package.json)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user (security best practice)
RUN addgroup -S taskforge && adduser -S taskforge -G taskforge

COPY --from=builder /app/target/*.jar app.jar

USER taskforge

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and test locally:
```bash
docker build -t taskforge:latest .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/taskforge_dev \
  -e DATABASE_USERNAME=taskforge \
  -e DATABASE_PASSWORD=password \
  -e JWT_SECRET=your-256-bit-secret \
  -e SPRING_PROFILES_ACTIVE=prod \
  taskforge:latest
```

---

## Step 3: Docker Compose

Create `docker-compose.yml` in `capstone/taskforge/`:

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:15-alpine
    container_name: taskforge-db
    environment:
      POSTGRES_USER: taskforge
      POSTGRES_PASSWORD: password
      POSTGRES_DB: taskforge_prod
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U taskforge"]
      interval: 10s
      timeout: 5s
      retries: 5
    ports:
      - "5432:5432"

  app:
    build: .
    container_name: taskforge-api
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/taskforge_prod
      DATABASE_USERNAME: taskforge
      DATABASE_PASSWORD: password
      JWT_SECRET: your-super-secret-256-bit-key-change-this-in-production
    ports:
      - "8080:8080"

volumes:
  postgres_data:
```

Start the full stack:
```bash
docker compose up --build

# Verify it's running
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@taskforge.com","password":"SecurePass123!"}'
```

---

## Step 4: Database Migration (Flyway)

In production, **never use `ddl-auto: create` or `update`**. Use Flyway for versioned schema migrations (same idea as Knex migrations or Prisma migrate).

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

Create migration files in `src/main/resources/db/migration/`:

```
db/migration/
├── V1__create_users_table.sql
├── V2__create_projects_table.sql
├── V3__create_tasks_table.sql
└── V4__create_comments_table.sql
```

Example `V1__create_users_table.sql`:
```sql
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

Update `application-prod.yml`:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway handles schema, JPA just validates
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## Step 5: Final Validation Checklist

Run through this checklist before calling TaskForge "done":

```
□ Application starts cleanly with SPRING_PROFILES_ACTIVE=prod
□ All unit tests pass: mvn test
□ All integration tests pass
□ /actuator/health returns {"status":"UP"}
□ POST /api/auth/register works
□ POST /api/auth/login returns a JWT
□ Authenticated endpoints reject requests without a valid JWT (401)
□ ADMIN endpoints reject non-admin users (403)
□ Docker Compose brings up the full stack
□ Logs show no stack traces or WARN/ERROR entries on startup
□ No secrets hardcoded in any source file
```

---

## What's Next?

Congratulations on completing the TaskForge tutorial! Here are some next steps to deepen your Spring Boot knowledge:

| Topic | Resources |
|-------|-----------|
| **Spring Batch** | Processing large datasets — great for ETL pipelines |
| **Spring WebFlux** | Reactive, non-blocking web framework (like Node's event loop — but for Java) |
| **Spring GraphQL** | GraphQL API support |
| **Testcontainers** | Integration tests against real Docker containers instead of H2 |
| **Spring Cloud Sleuth / Zipkin** | Distributed tracing for microservices |
| **GraalVM Native Image** | Compile Spring Boot to a native binary (zero-JVM startup time) |
| **Spring Authorization Server** | Full OAuth2 authorization server implementation |

---

## Related Roadmaps

From the [Spring Boot Roadmap](https://roadmap.sh/spring-boot):
- [Backend Roadmap](https://roadmap.sh/backend)
- [Java Roadmap](https://roadmap.sh/java)
- [Docker Roadmap](https://roadmap.sh/docker)
- [DevOps Roadmap](https://roadmap.sh/devops)
