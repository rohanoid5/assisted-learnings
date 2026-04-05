# 2.1 — Spring Boot Starters

## Concept

A **Spring Boot Starter** is a curated set of Maven dependencies bundled into a single dependency. Instead of hunting for compatible versions of 12 libraries, you add one starter and get everything you need.

**Node.js analogy:** Starters are like npm meta-packages. Instead of installing `express`, `body-parser`, `cors`, and `compression` separately, you install `create-next-app` and get a pre-configured, compatible stack. Even closer: `@nestjs/platform-express` pulls in everything NestJS needs for Express.

```json
// Node.js — manual, version hell
{
  "dependencies": {
    "express": "^4.18.0",
    "body-parser": "^1.20.0",
    "cors": "^2.8.5",
    "helmet": "^7.0.0",
    "jsonwebtoken": "^9.0.0",
    "@types/express": "...",
    "@types/jsonwebtoken": "..."
  }
}
```

```xml
<!-- Spring Boot — one starter, compatible versions guaranteed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <!-- version inherited from spring-boot-starter-parent -->
</dependency>
```

---

## How Starters Work

A starter is just a `pom.xml` that depends on other libraries. `spring-boot-starter-web`, for example, brings in:

```
spring-boot-starter-web
├── spring-boot-starter (core Boot support)
│   ├── spring-boot
│   ├── spring-boot-autoconfigure
│   └── spring-boot-starter-logging (Logback)
├── spring-boot-starter-json
│   └── jackson-databind (JSON serialization)
├── spring-boot-starter-tomcat
│   └── tomcat-embed-core (embedded Tomcat)
└── spring-webmvc (DispatcherServlet, controllers, etc.)
```

All of these are pulled in transitively with **compatible versions** — no `peerDependencies` hell.

---

## Parent POM: Version Management

All Spring Boot projects inherit from the **parent POM**, which manages ~300 dependency versions:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
</parent>

<dependencies>
    <!-- No version needed — parent manages it -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- PostgreSQL version is also managed by parent -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

You only specify a version if you want to **override** the managed version.

---

## Essential Starters Reference

| Starter | What it provides | When to add |
|---------|-----------------|-------------|
| `spring-boot-starter-web` | Spring MVC, Tomcat, Jackson | Any REST API |
| `spring-boot-starter-data-jpa` | Hibernate, JPA, Spring Data | Relational DB (PostgreSQL, MySQL) |
| `spring-boot-starter-security` | Spring Security | Authentication & authorization |
| `spring-boot-starter-validation` | Bean Validation (Hibernate Validator) | Input validation (`@Valid`, `@NotNull`) |
| `spring-boot-starter-actuator` | Health, metrics, info endpoints | Operational visibility |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, MockMvc | Testing (included by default) |
| `spring-boot-starter-aop` | Spring AOP, AspectJ | Aspects, `@Transactional` internals |
| `spring-boot-starter-cache` | Spring Cache abstraction | `@Cacheable`, `@CacheEvict` |
| `spring-boot-starter-mail` | JavaMail | Sending emails |
| `spring-boot-starter-data-mongodb` | Spring Data MongoDB | MongoDB |
| `spring-boot-starter-data-redis` | Spring Data Redis | Redis caching / sessions |
| `spring-boot-starter-websocket` | WebSocket support | Real-time features |
| `spring-boot-starter-oauth2-resource-server` | OAuth2 JWT resource server | OAuth2/OIDC auth |

**Third-party starters** (not official but common):

| Starter | What it provides |
|---------|-----------------|
| `org.mapstruct:mapstruct` | DTO ↔ Entity mapping |
| `io.jsonwebtoken:jjwt-*` | JWT generation/validation |
| `org.projectlombok:lombok` | Boilerplate reduction (`@Getter`, `@Builder`, etc.) |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Swagger / OpenAPI docs |

---

## TaskForge's `pom.xml` Dependencies

Here's what TaskForge's complete dependency list looks like (you'll add starters progressively):

```xml
<dependencies>
    <!-- Module 2: Web layer + Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Module 2: Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Module 2: AOP -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- Module 4: Database -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Module 5: Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>

    <!-- Testing (always included) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Try It Yourself

**Exercise:** Explore what a starter pulls in.

1. In your `pom.xml`, right-click `spring-boot-starter-web` in IntelliJ → **Maven → Show Effective POM**
2. Or run: `mvn dependency:tree -Dincludes=org.springframework.boot`
3. Find and note: Which version of Tomcat does `spring-boot-starter-web` bring in?

<details>
<summary>Answer (Spring Boot 3.2.x)</summary>

Spring Boot 3.2.x uses **Tomcat 10.1.x** (which supports Jakarta EE 10 — this is why imports changed from `javax.*` to `jakarta.*` in Spring Boot 3.x).

```
[INFO] com.taskforge:taskforge:jar:0.0.1-SNAPSHOT
[INFO] +- org.springframework.boot:spring-boot-starter-web:jar:3.2.3
[INFO] |  +- org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.3
[INFO] |  |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.19
```
</details>

---

## Capstone Connection

You added `spring-boot-starter-web` and `spring-boot-starter-actuator` when generating the project. In Module 4, you'll add `spring-boot-starter-data-jpa` and `postgresql`. In Module 5, you'll add `spring-boot-starter-security`. Watch how each starter automatically configures new capabilities — that's autoconfiguration in action (next topic).
