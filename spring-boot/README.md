# Spring Boot Interactive Tutorial

A hands-on, modular Spring Boot learning guide built for experienced **JavaScript / TypeScript / Node.js** developers. Every concept is taught with a Node.js or Express analogy so you can map patterns you already know onto the Spring ecosystem — no re-learning from scratch.

---

## How to Use This Tutorial

1. Work through modules **in order** — each builds on the previous.
2. After each concept, **apply it to the capstone project** (TaskForge) using the _Capstone Connection_ section at the bottom of every topic file.
3. Complete the **exercises** before moving to the next module.
4. The `capstone/taskforge-solution/` folder has a reference implementation if you get stuck.

> You don't need to memorize everything. Focus on the mental model first — the syntax comes with practice.

---

## Prerequisites

| Requirement       | Version | Notes                                                               |
| ----------------- | ------- | ------------------------------------------------------------------- |
| JDK               | 17+     | [Adoptium / Eclipse Temurin](https://adoptium.net/)                 |
| Maven             | 3.9+    | Bundled with IntelliJ; or install via `brew install maven`          |
| IntelliJ IDEA     | Latest  | Community edition is free and excellent for Spring Boot             |
| PostgreSQL        | 15+     | Only needed from Module 4 onwards                                   |
| Docker            | Latest  | For running PostgreSQL easily, and required for Module 7            |
| Postman / HTTPie  | Latest  | For manually testing REST endpoints                                 |

> **Java background check:** You should be comfortable with Java classes, generics, streams, and lambdas before starting. The [Java Roadmap](https://roadmap.sh/java) is a solid companion resource if you need a refresher.

---

## Learning Path

| Module | Topic | Est. Time | Capstone Milestone |
|--------|-------|-----------|-------------------|
| [01 — Introduction](modules/01-introduction/) | Terminology, Architecture, DI, IoC, AOP, Annotations | 4–6 hrs | Bootstrap TaskForge project |
| [02 — Spring Boot Core](modules/02-spring-boot-core/) | Starters, Autoconfiguration, Embedded Server, Actuators | 2–3 hrs | Health checks + Actuator endpoints |
| [03 — Spring MVC](modules/03-spring-mvc/) | Servlet, MVC Architecture, REST Controllers, Validation | 3–5 hrs | Full CRUD REST API |
| [04 — Spring Data](modules/04-spring-data/) | Hibernate, JPA, Relationships, Transactions | 5–7 hrs | PostgreSQL persistence |
| [05 — Spring Security](modules/05-spring-security/) | Authentication, Authorization, JWT, OAuth2 | 4–6 hrs | Secured endpoints with JWT |
| [06 — Testing](modules/06-testing/) | @SpringBootTest, MockMvc, @MockBean, @DataJpaTest | 3–5 hrs | Full unit + integration test suite |
| [07 — Microservices *(advanced)*](modules/07-microservices/) | Spring Cloud, Gateway, Eureka, OpenFeign, Resilience4j | 6–8 hrs | Microservices refactor |
| [08 — Capstone Integration](modules/08-capstone-integration/) | Docker Compose, deployment, production readiness | 2–4 hrs | Production-ready TaskForge |

**Total estimated time:** 30–45 hours

---

## Capstone Project: TaskForge

TaskForge is a **simplified Jira / Linear-style project management API** that you build incrementally throughout this tutorial. Each module adds a new layer — from bare scaffolding to a production-ready, secured, tested application.

### Domain Model

```
User ──owns──▶ Project ──has──▶ Task ──has──▶ Comment
 │                │               │
 │                └──members──▶ User    └──assignee──▶ User
 │
 └──role: ADMIN | MANAGER | USER
```

### Entities

| Entity    | Key Fields |
|-----------|-----------|
| `User`    | id, username, email, password, role (ADMIN/MANAGER/USER) |
| `Project` | id, name, description, owner (User), members (Set\<User\>) |
| `Task`    | id, title, description, status (TODO/IN_PROGRESS/REVIEW/DONE), priority (LOW/MEDIUM/HIGH/CRITICAL), assignee (User), project (Project) |
| `Comment` | id, content, author (User), task (Task) |

### What you'll build module-by-module

| Module | What gets added to TaskForge |
|--------|------------------------------|
| 01–02  | Project scaffolding, configuration profiles, actuator endpoints |
| 03     | REST controllers for all 4 entities — full CRUD |
| 04     | PostgreSQL via JPA — entities, relationships, custom queries, pagination |
| 05     | JWT authentication, role-based access control |
| 06     | Unit tests, integration tests, repository tests |
| 07     | Decompose into user-service, project-service, task-service (optional) |
| 08     | Docker Compose, production config, deployment checklist |

The capstone lives in [`capstone/taskforge/`](capstone/taskforge/). A complete reference solution is in [`capstone/taskforge-solution/`](capstone/taskforge-solution/).

---

## Quick Start

```bash
# Navigate to the workspace
cd ~/Desktop/personal/learnings/spring-boot

# Start with Module 1
open modules/01-introduction/README.md

# When you reach Module 4, spin up PostgreSQL via Docker
docker run --name taskforge-db \
  -e POSTGRES_USER=taskforge \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=taskforge_dev \
  -p 5432:5432 \
  -d postgres:15

# Build and run the capstone at any point
cd capstone/taskforge
mvn clean install
mvn spring-boot:run
```

---

## Node.js → Spring Boot Mental Model

| Node.js / Express / NestJS | Spring Boot |
|---------------------------|-------------|
| `package.json` | `pom.xml` |
| `npm install` | `mvn install` |
| `node index.js` | `mvn spring-boot:run` |
| `.env` file | `application.yml` / `application.properties` |
| `process.env.PORT` | `${server.port}` in application.yml |
| Express Router | `@RestController` + `@RequestMapping` |
| `app.use(middleware)` | Spring Filter / `@Aspect` |
| Route handler `(req, res) => {}` | `@GetMapping` / `@PostMapping` method |
| `async/await` | `@Async` + `CompletableFuture` |
| `jest.mock()` | `@MockBean` |
| `supertest` | `MockMvc` |
| Passport.js middleware chain | `SecurityFilterChain` |
| TypeORM / Prisma entity | `@Entity` class |
| TypeORM Repository | `JpaRepository<Entity, ID>` |
| `@Module()` (NestJS) | `@Configuration` class |
| `@Injectable()` (NestJS) | `@Service` / `@Component` |
| TypeScript decorators | Java annotations |
| `Joi` / `Zod` validation | `@Valid` + Bean Validation (`@NotNull`, `@Size`, …) |
| Express error middleware | `@ControllerAdvice` + `@ExceptionHandler` |
| `npm` registry | Maven Central |

---

## Project Structure

```
spring-boot/
├── README.md                         ← You are here
├── roadmap.png
├── modules/
│   ├── 01-introduction/
│   ├── 02-spring-boot-core/
│   ├── 03-spring-mvc/
│   ├── 04-spring-data/
│   ├── 05-spring-security/
│   ├── 06-testing/
│   ├── 07-microservices/
│   └── 08-capstone-integration/
└── capstone/
    ├── taskforge/                    ← Your working project
    └── taskforge-solution/           ← Reference implementation
```
