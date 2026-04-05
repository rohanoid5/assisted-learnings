# 2.2 — Autoconfiguration

## Concept

Autoconfiguration is Spring Boot's superpower: the ability to configure your application **automatically** based on what's on the classpath, without you writing a single configuration class.

**Node.js analogy:** It's like if adding `"express"` to your `package.json` automatically created an `app.listen(3000)` somewhere, registered JSON body parsing, and configured CORS — all without you writing any code. You could still override any of it, but the defaults just work.

---

## How It Works

When Spring Boot starts, it runs through a list of ~300 autoconfiguration classes. Each one is **conditional** — it only activates if certain conditions are met.

### The Mechanism

```
Spring Boot startup
      │
      ▼
Read META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
(or spring.factories in older versions)
      │
      ▼
For each AutoConfiguration class:
  ├── @ConditionalOnClass(DataSource.class)  → Only if DataSource class is on classpath?
  ├── @ConditionalOnMissingBean(DataSource)  → Only if YOU haven't defined a DataSource bean?
  ├── @ConditionalOnProperty("spring.datasource.url")  → Only if property is set?
  └── → If all conditions pass: create the beans
```

### Example: `DataSourceAutoConfiguration`

When you add `spring-boot-starter-data-jpa` to your `pom.xml`:
- `HikariCP` (connection pool) lands on the classpath
- `DataSourceAutoConfiguration` detects it via `@ConditionalOnClass(HikariDataSource.class)`
- It reads `spring.datasource.*` properties from your `application.yml`
- It creates a `DataSource` bean (HikariCP pool) — automatically, no code from you

```java
// This is a simplified version of what Spring Boot does internally:
@AutoConfiguration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource(DataSourceProperties properties) {
        // Creates HikariCP pool from your application.yml settings
        return properties.initializeDataSourceBuilder().build();
    }
}
```

The key annotation is `@ConditionalOnMissingBean` — if **you** define a `DataSource` bean in your code, this autoconfiguration skips itself entirely. **Your configuration always wins.**

---

## Common Autoconfiguration Classes

| Autoconfiguration | Triggered when | What it creates |
|------------------|---------------|-----------------|
| `DataSourceAutoConfiguration` | `DataSource` class on classpath, `spring.datasource.url` set | HikariCP connection pool |
| `HibernateJpaAutoConfiguration` | `EntityManagerFactory` class + DataSource exists | JPA EntityManagerFactory |
| `DispatcherServletAutoConfiguration` | Spring MVC on classpath | `DispatcherServlet` — the HTTP router |
| `SecurityAutoConfiguration` | spring-security on classpath | Default security (Basic Auth, login page) |
| `JacksonAutoConfiguration` | Jackson on classpath | Configured `ObjectMapper` for JSON |
| `ActuatorAutoConfiguration` | Actuator on classpath | Health/metrics/info endpoints |
| `H2ConsoleAutoConfiguration` | H2 + devtools on classpath + dev profile | H2 web console at `/h2-console` |
| `CacheAutoConfiguration` | `@EnableCaching` present | Cache manager |

---

## Viewing Autoconfiguration Report

To see exactly what was and wasn't autoconfigured:

```bash
# Add this to application-dev.yml:
logging:
  level:
    org.springframework.boot.autoconfigure: DEBUG

# Or run with:
mvn spring-boot:run --spring.debug=true
```

You'll see a report like:
```
=========================
AUTO-CONFIGURATION REPORT
=========================

Positive matches (autoconfiguration that DID activate):
---------------------------------------------------------
DataSourceAutoConfiguration matched:
   - @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)

Negative matches (autoconfiguration that did NOT activate):
------------------------------------------------------------
MongoAutoConfiguration:
   Did not match:
      - @ConditionalOnClass did not find required class 'com.mongodb.MongoClient' (OnClassCondition)
```

---

## Overriding Autoconfiguration

You always have full control. Override just what you need:

### Override a single bean

```java
@Configuration
public class JacksonConfig {

    // Since you're defining an ObjectMapper bean, Spring Boot's auto-configured one
    // won't be created (@ConditionalOnMissingBean)
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
```

### Disable an autoconfiguration entirely

```java
// In your main class or a @Configuration class
@SpringBootApplication(exclude = {
    SecurityAutoConfiguration.class,          // Disable auto-security (you'll configure manually)
    DataSourceAutoConfiguration.class         // Disable if you don't need a DB
})
public class TaskforgeApplication { ... }
```

Or in `application.yml`:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

---

## Autoconfiguration with Properties

Autoconfiguration reads your `application.yml` to configure beans. These are the **common property namespaces**:

```yaml
spring:
  datasource:          # DataSourceAutoConfiguration
    url: ...
    username: ...
  jpa:                 # JpaBaseConfiguration
    hibernate:
      ddl-auto: update
    show-sql: true
  security:            # SecurityProperties
    user:
      name: admin
  jackson:             # JacksonProperties
    date-format: yyyy-MM-dd
    serialization:
      write-dates-as-timestamps: false

server:
  port: 8080           # ServerProperties
  servlet:
    context-path: /api
```

---

## Try It Yourself

**Exercise:** See autoconfiguration in action.

1. Your TaskForge project currently has `spring-boot-starter-web`. Run the app.

2. Add `spring-security` to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

3. Run the app again and try `curl http://localhost:8080/api/status`. What happens?

<details>
<summary>Answer</summary>

You'll get a 401 Unauthorized response! Spring Security's autoconfiguration detected the security starter and automatically secured all endpoints. It also generated a random password printed in the logs:

```
Using generated security password: a1b2c3d4-xxxx
```

This is autoconfiguration at work — you added one dependency and your entire API became secured, without writing a single line of configuration. (You'll override this with your own JWT security config in Module 5.)

For now, either:
- Remove the dependency (we'll re-add it in Module 5)
- Or exclude `SecurityAutoConfiguration` temporarily
</details>

---

## Capstone Connection

Every capability in TaskForge except your own business logic is provided by autoconfiguration:
- Web server: `DispatcherServletAutoConfiguration`
- JSON parsing: `JacksonAutoConfiguration`
- Database connection pool: `DataSourceAutoConfiguration`
- JPA entity manager: `HibernateJpaAutoConfiguration`
- Security filter chain: `SecurityAutoConfiguration` (overridden by your `SecurityConfig` in Module 5)
- Health endpoints: `ActuatorAutoConfiguration`
