# Plan: Spring Boot Interactive Tutorial & Capstone

Build a modular, markdown-based tutorial covering every topic in the roadmap, with **Node.js/Express analogies** throughout (leveraging your 7 years of JS/TS experience). A **"TaskForge"** project management API (simplified Jira) is the capstone — built incrementally as you learn each module, so every concept is immediately applied.

---

## Structure

```
spring-boot/
├── README.md                        # Overview, prerequisites, how to navigate
├── capstone/taskforge/              # Your working project (built module-by-module)
├── modules/
│   ├── 01-introduction/             # 9 topic files
│   ├── 02-spring-boot-core/         # 4 topic files
│   ├── 03-spring-mvc/               # 4 topic files
│   ├── 04-spring-data/              # 7 topic files
│   ├── 05-spring-security/          # 4 topic files
│   ├── 06-testing/                  # 4 topic files
│   ├── 07-microservices/            # 7 topic files (advanced)
│   └── 08-capstone-integration/     # Final integration & deployment
```

## Modules

### Module 1 — Introduction
**Topics:** Terminology, Architecture, Why Spring, Configuration, DI, IoC, AOP, Annotations, Bean Scope

**Node.js analogy:** DI ≈ NestJS constructor injection; IoC container ≈ NestJS modules; AOP ≈ Express middleware; Annotations ≈ TS decorators

**Capstone milestone:** Bootstrap TaskForge, set up configuration profiles (dev/prod)

### Module 2 — Spring Boot Core
**Topics:** Starters, Autoconfiguration, Embedded Server, Actuators

**Node.js analogy:** Starters ≈ npm meta-packages; Autoconfiguration ≈ Next.js conventions; Embedded Server ≈ Node's HTTP server; Actuators ≈ health-check middleware

**Capstone milestone:** Add actuator endpoints, custom health indicators

### Module 3 — Spring MVC
**Topics:** Servlet, Architecture, Components, REST Controllers

**Node.js analogy:** DispatcherServlet ≈ Express router; `@RestController` ≈ route handlers; `@Valid` ≈ Joi/Zod; `@ControllerAdvice` ≈ Express error middleware

**Capstone milestone:** Full CRUD API — Projects, Tasks, Users, Comments

### Module 4 — Spring Data & Hibernate
**Topics:** Entity Lifecycle, Relationships, Transactions, JPA, MongoDB, JDBC

**Node.js analogy:** Hibernate/JPA ≈ TypeORM/Prisma; Entity ≈ Model; Repository pattern same concept

**Capstone milestone:** PostgreSQL persistence, entity relationships, pagination, custom queries

### Module 5 — Spring Security
**Topics:** Authentication, Authorization, OAuth2, JWT

**Node.js analogy:** SecurityFilterChain ≈ Passport.js middleware; `@PreAuthorize` ≈ role middleware; JWT flow same concept

**Capstone milestone:** JWT auth, role-based access (Admin/Manager/User)

### Module 6 — Testing
**Topics:** @SpringBootTest, @MockBean, MockMvc, JPA Test

**Node.js analogy:** MockMvc ≈ supertest; `@MockBean` ≈ `jest.mock()`; `@DataJpaTest` ≈ test containers

**Capstone milestone:** Unit, integration, and E2E tests for TaskForge

### Module 7 — Microservices & Spring Cloud (Advanced)
**Topics:** Gateway, Config Server, Eureka, OpenFeign, Circuit Breaker, Micrometer

**Node.js analogy:** Gateway ≈ nginx; Eureka ≈ Consul; OpenFeign ≈ axios + service discovery; Circuit Breaker ≈ opossum

**Capstone milestone:** Refactor TaskForge into microservices (optional advanced)

## Tutorial Format (per topic file)

Each `.md` file follows this pattern:
1. **Concept** — What it is + Node.js/Express analogy
2. **How It Works** — Technical explanation
3. **Code Example** — Annotated, runnable snippet
4. **Try It Yourself** — Mini-exercise with collapsible solution
5. **Capstone Connection** — How to apply this in TaskForge

## Capstone: TaskForge

**Domain Model:**
- **User** — username, email, password, role (ADMIN/MANAGER/USER)
- **Project** — name, description, owner, members
- **Task** — title, description, status (TODO/IN_PROGRESS/REVIEW/DONE), priority, assignee, project
- **Comment** — content, author, task

Built incrementally: scaffolding → REST API → database → security → tests → microservices.

---

## Steps

| Phase | Steps | Notes |
|---|---|---|
| **1. Setup** | Create directory structure, root `README.md`, initialize TaskForge via Spring Initializr | Independent |
| **2. Core Modules** | Write Modules 1→6 sequentially (each builds on prior) | ~39 topic files total |
| **3. Advanced** | Write Module 7 (Microservices) | Optional/advanced |
| **4. Integration** | Write Module 8 + deployment guide (Docker Compose) | Depends on all above |
| **5. Reference Code** | Create TaskForge project skeleton (`pom.xml`, entities, repos, services, controllers) | Fully annotated reference |

## Verification
1. Every code example compiles independently
2. Capstone builds with `mvn clean install`
3. All capstone tests pass
4. No forward references to unexplained concepts

## Decisions
- **Java 17** (LTS), **Maven** (Gradle mentioned as alternative)
- **PostgreSQL** for primary DB, **H2** for testing
- **JSP** mentioned briefly — modern Spring Boot uses REST + SPA
- **MongoDB/JDBC** covered for awareness; capstone uses JPA only
- Markdown with embedded code blocks (not separate `.java` files per example)

## Further Considerations
1. **Microservices scope:** Full multi-service Docker Compose setup, or conceptual with snippets? *Recommendation: Provide Docker Compose but mark as optional/advanced.*
2. **Reference implementation:** Complete final code in a `solution/` folder, or leave `capstone/` empty for you to build? *Recommendation: Provide reference in `solution/`, keep `capstone/` as your workspace.*
3. **Any specific topics you want more/less depth on?** e.g., skip MongoDB coverage, go deeper on security, etc.
