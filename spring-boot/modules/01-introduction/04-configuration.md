# 1.4 — Configuration

## Concept

Every app needs configuration: database URLs, ports, secrets, feature flags. In Node.js you use `.env` files with `dotenv` and `process.env.VARIABLE`. Spring Boot uses `application.yml` (or `application.properties`) and injects values via annotations.

The key advantages over Node.js `.env`:
1. **Type-safe** — values are bound to typed Java fields, not raw strings
2. **Nested structure** — YAML supports deep hierarchies without `MY_DB_HOST`-style naming
3. **Built-in profiles** — no need to manage `NODE_ENV` manually and check it everywhere

---

## `application.properties` vs `application.yml`

Both work — `application.yml` is more readable for nested config:

```properties
# application.properties (flat, verbose)
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/taskforge
spring.datasource.username=taskforge
spring.datasource.password=password
app.jwt.secret=my-secret-key
app.jwt.expiration-ms=86400000
```

```yaml
# application.yml (nested, readable) ← Preferred
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskforge
    username: taskforge
    password: password

app:
  jwt:
    secret: my-secret-key
    expiration-ms: 86400000
```

Spring Boot automatically reads `src/main/resources/application.yml` on startup.

---

## Profiles

Profiles are Spring Boot's way of supporting `NODE_ENV`. You create a base `application.yml` and profile-specific override files:

```
src/main/resources/
├── application.yml          ← Base config (shared across all environments)
├── application-dev.yml      ← Dev overrides (activated when profile = dev)
├── application-prod.yml     ← Prod overrides
└── application-test.yml     ← Test overrides (H2 in-memory, etc.)
```

**`application.yml` (base):**
```yaml
server:
  port: 8080

app:
  jwt:
    expiration-ms: 86400000    # 24 hours

spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
```

**`application-dev.yml` (dev overrides):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskforge_dev
    username: taskforge
    password: password
  jpa:
    hibernate:
      ddl-auto: update          # Auto-create/update tables in dev
    show-sql: true              # Log SQL in dev

app:
  jwt:
    secret: dev-secret-key-not-for-production

logging:
  level:
    com.taskforge: DEBUG
    org.hibernate.SQL: DEBUG
```

**`application-prod.yml` (prod overrides):**
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}        # Inject from environment variable
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate        # Never auto-modify schema in production

app:
  jwt:
    secret: ${JWT_SECRET}       # Never hardcode secrets in prod

logging:
  level:
    root: WARN
    com.taskforge: INFO
```

**Activating a profile:**
```bash
# Via environment variable (recommended for production)
SPRING_PROFILES_ACTIVE=prod java -jar app.jar

# Via Maven (for local dev)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# In IntelliJ: Run → Edit Configurations → Active profiles: dev
```

The active profile's values **override** the base `application.yml` values. Same key = override.

---

## Injecting Values with `@Value`

The simplest way to use a config value — similar to reading `process.env.MY_VAR`:

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    // Equivalent to: const secret = process.env.JWT_SECRET
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // You can also provide a default value
    @Value("${app.feature.dark-mode:false}")
    private boolean darkModeEnabled;

    public String generateToken(String username) {
        // Use jwtSecret and expirationMs here
        return "...";
    }
}
```

**Limitation:** `@Value` on individual fields gets messy for large config trees. That's where `@ConfigurationProperties` comes in.

---

## Type-Safe Config with `@ConfigurationProperties`

This is the **recommended approach** for groups of related config values. It binds an entire YAML subtree to a Java class:

```yaml
# application.yml
app:
  jwt:
    secret: my-secret-key
    expiration-ms: 86400000
  cors:
    allowed-origins:
      - http://localhost:3000
      - https://taskforge.io
    allowed-methods:
      - GET
      - POST
      - PUT
      - DELETE
```

```java
// AppProperties.java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")  // Binds properties starting with "app."
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();

    // Getters required for binding
    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }

    public static class Jwt {
        private String secret;
        private long expirationMs;
        // getters + setters required
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    }

    public static class Cors {
        private List<String> allowedOrigins;
        private List<String> allowedMethods;
        // getters + setters
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
    }
}
```

Modern Spring Boot 3.x supports **records** for a cleaner syntax:

```java
// Modern approach using records (Java 16+)
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {}
```

```java
// Enable it in your main class or a @Configuration class:
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TaskforgeApplication { ... }
```

Usage:
```java
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {  // Injected via constructor
        this.jwtProperties = jwtProperties;
    }

    public long getExpirationMs() {
        return jwtProperties.expirationMs();  // Record accessor
    }
}
```

---

## Environment Variables Override

Just like in Node.js, environment variables take priority over file-based config. Spring Boot automatically converts `DATABASE_URL` (env var) → `spring.datasource.url` (property):

```bash
# These env vars will override application.yml:
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/taskforge
export SPRING_DATASOURCE_USERNAME=prod_user
export APP_JWT_SECRET=super-secret-key
```

**Priority (highest to lowest):**
1. Command-line arguments (`--server.port=9090`)
2. Environment variables
3. Profile-specific `application-{profile}.yml`
4. Base `application.yml`

---

## Try It Yourself

**Exercise:** Add the TaskForge app config to your project.

1. Create `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: taskforge

app:
  jwt:
    secret: dev-secret-key-minimum-256-bits-long
    expiration-ms: 86400000
```

2. Create `src/main/resources/application-dev.yml` with the datasource config shown above.

3. Create a `@ConfigurationProperties` class called `JwtProperties` bound to `app.jwt`.

4. Inject `JwtProperties` into any existing `@Service` and print `jwtProperties.expirationMs()` using `System.out.println()` in a no-arg constructor.

5. Run with `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` and verify the value prints.

<details>
<summary>Verification: what to look for in the console</summary>

You should see something like:
```
JWT expiration: 86400000 ms
```
If you get a `BindException`, check that your YAML indentation is correct (YAML is whitespace-sensitive) and that the property names match (Spring converts `camelCase` ↔ `kebab-case` automatically).
</details>

---

## Capstone Connection

TaskForge uses three config files:
- `application.yml` — base config (port, app name, JWT expiration)
- `application-dev.yml` — local PostgreSQL, SQL logging, DEBUG level
- `application-prod.yml` — environment variable refs, no SQL logging, WARNING level

All secrets (JWT secret, DB password) are referenced as `${ENV_VAR}` — never hardcoded.
