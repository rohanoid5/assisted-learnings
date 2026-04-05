# 1.1 — Terminology: Spring vs Spring Boot

## Concept

There's a lot of "Spring" branding, and it can be confusing at first. Let's establish precise definitions before anything else.

### Spring Framework

The **Spring Framework** is a comprehensive Java application framework launched in 2003 as a reaction to the complexity of Java EE (Enterprise Edition). Its core innovation was **Inversion of Control (IoC)** — instead of your code creating objects and wiring them together, the framework does it for you.

Spring Framework is modular: you pick the parts you need (Spring MVC, Spring Data, Spring Security, etc.) and configure them yourself. The downside? Lots of XML configuration (historically) and boilerplate.

### Spring Boot

**Spring Boot** (launched 2014) is *not* a replacement for Spring Framework — it's a launcher and autoconfigurer built *on top of* Spring Framework. It applies the principle of **convention over configuration**:

> "If you have a datasource dependency on the classpath, we'll assume you want a database connection and configure one for you automatically."

Spring Boot eliminates most boilerplate configuration. You get a production-ready app with almost no setup.

```
┌─────────────────────────────────────────────────┐
│                  Spring Boot                     │
│  (autoconfiguration, starters, embedded server)  │
│  ┌───────────────────────────────────────────┐  │
│  │            Spring Framework               │  │
│  │  (IoC, DI, MVC, Data, Security, AOP, …)  │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**Analogy for Node.js devs:** Spring Framework is like Express (raw, flexible, you configure everything). Spring Boot is like Next.js or NestJS (opinionated, pre-configured, gets out of your way).

---

## Key Vocabulary Reference

These terms will come up constantly. Bookmark this page.

| Term | Definition | Node.js Equivalent |
|------|-----------|-------------------|
| **Bean** | Any object managed by the Spring IoC container | A singleton service created by a DI container (NestJS `@Injectable`) |
| **IoC Container** | The Spring runtime that creates, configures, and manages beans | NestJS `ModuleRef` / the DI system |
| **ApplicationContext** | The central interface to the IoC container — your access point to all beans | `app.get(SomeService)` in NestJS |
| **Dependency Injection (DI)** | The mechanism by which the container provides a bean's dependencies | Constructor injection in NestJS |
| **Annotation** | Java metadata (`@Something`) that tells Spring how to handle a class/method | TypeScript/NestJS decorator (`@Injectable()`, `@Get()`) |
| **Auto-configuration** | Spring Boot's automatic configuration of beans based on classpath contents | Convention-over-configuration (Next.js, NestJS) |
| **Starter** | A curated set of Maven dependencies for a specific feature | An npm meta-package (e.g., `create-next-app` includes React, ReactDOM, etc.) |
| **Profile** | A named environment configuration (`dev`, `prod`, `test`) | `NODE_ENV` + multiple `.env` files |
| **AOP** | Aspect-Oriented Programming — adding cross-cutting behavior (logging, security) | Express middleware |
| **Spring MVC** | Spring's web framework for handling HTTP requests | Express.js |
| **DispatcherServlet** | The central HTTP request router in Spring MVC | Express app's routing layer |
| **JPA** | Java Persistence API — the ORM specification | The TypeORM interface / Prisma schema |
| **Hibernate** | The most popular JPA implementation | TypeORM / Sequelize |
| **Entity** | A Java class mapped to a database table | TypeORM `@Entity()` class / Prisma model |
| **Repository** | Interface for database operations | TypeORM repository / Prisma client |

---

## Spring vs Spring Boot: Side-by-Side

| Concern | Spring Framework | Spring Boot |
|---------|-----------------|-------------|
| Project setup | Manual dependency management | Spring Initializr (web UI or CLI) |
| Configuration | XML or Java `@Configuration` classes (verbose) | `application.yml` + autoconfiguration |
| Web server | Deploy WAR to external Tomcat | Embedded Tomcat inside the JAR |
| Running the app | Deploy to app server | `java -jar app.jar` or `mvn spring-boot:run` |
| Getting started | Days of setup | Minutes with Initializr |

---

## The Spring Ecosystem Map

```
Spring Boot (foundation)
├── Spring MVC            → REST APIs and web apps
├── Spring Data           → Database access (JPA, MongoDB, JDBC, Redis, …)
├── Spring Security       → Authentication and authorization
├── Spring Cloud          → Microservices (Gateway, Config, Eureka, Feign, …)
├── Spring Batch          → Bulk data processing
├── Spring WebFlux        → Reactive / non-blocking web (≈ Node's event loop)
├── Spring Integration    → Enterprise integration patterns (messaging, etc.)
└── Spring GraphQL        → GraphQL API support
```

You'll learn Spring MVC, Spring Data, Spring Security, and Spring Cloud in this tutorial.

---

## Try It Yourself

**Exercise:** Generate your first Spring Boot project.

1. Go to [https://start.spring.io](https://start.spring.io)
2. Configure it:
   - Project: **Maven**
   - Language: **Java**
   - Spring Boot: **3.2.x** (latest stable)
   - Group: `com.taskforge`
   - Artifact: `taskforge`
   - Java: **17**
3. Add dependencies: **Spring Web**, **Spring Boot Actuator**
4. Click **Generate** and unzip the project
5. Open it in IntelliJ IDEA
6. Run `mvn spring-boot:run` — you should see the banner and `Started TaskforgeApplication`

<details>
<summary>What you should see</summary>

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.x)

2026-03-27T10:00:00.000Z  INFO Started TaskforgeApplication in 2.341 seconds
```

Ctrl+C to stop.
</details>

---

## Capstone Connection

The project you just generated in the exercise **is** your TaskForge starting point. Move it into `capstone/taskforge/` — you'll be building on it for the rest of the tutorial.
