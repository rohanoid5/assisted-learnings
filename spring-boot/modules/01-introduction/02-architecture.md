# 1.2 — Architecture

## Concept

Spring Boot applications follow a well-defined **layered architecture**. Understanding this architecture makes it immediately clear where each piece of code lives — no more wondering "should this logic go in the route handler or somewhere else?"

**Node.js/Express analogy:** If you've ever structured an Express app into `routes/`, `controllers/`, `services/`, and `models/` folders, you already know this pattern. Spring Boot just formalizes it with annotations.

---

## The Three Application Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                    HTTP Request (Client)                         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                  Presentation Layer                              │
│               (@RestController classes)                          │
│   • Receives HTTP requests                                       │
│   • Validates input                                              │
│   • Calls the service layer                                      │
│   • Returns HTTP responses                                       │
│   Node.js equiv: Express route handlers                          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    Business Layer                                │
│                  (@Service classes)                              │
│   • Contains business logic                                      │
│   • Orchestrates data access                                     │
│   • Applies business rules (auth checks, calculations, etc.)    │
│   • Manages transactions                                         │
│   Node.js equiv: Service classes in a services/ folder           │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                  Persistence Layer                               │
│               (@Repository interfaces)                           │
│   • Database access (JPA/Hibernate)                              │
│   • CRUD operations                                              │
│   • Custom queries                                               │
│   Node.js equiv: TypeORM repositories / Prisma client           │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                       Database                                   │
│               (PostgreSQL / H2 / MongoDB / …)                    │
└─────────────────────────────────────────────────────────────────┘
```

**Rule of thumb:**
- Controllers know about HTTP, not databases.
- Services know about business rules, not HTTP or database specifics.
- Repositories know about the database, not HTTP or business rules.

---

## The Spring Framework Module Architecture

Spring Framework itself is modular. Spring Boot brings in the modules you need via *starters* (covered in Module 2), but it helps to know what's available:

```
Spring Framework
│
├── Core Container
│   ├── spring-core        → Utilities, type conversion
│   ├── spring-beans       → Bean factory, DI
│   ├── spring-context     → ApplicationContext (the IoC container)
│   └── spring-expression  → Spring Expression Language (SpEL)
│
├── AOP & Instrumentation
│   └── spring-aop         → Aspect-oriented programming
│
├── Data Access / Integration
│   ├── spring-jdbc        → Low-level JDBC
│   ├── spring-orm         → JPA/Hibernate integration
│   ├── spring-tx          → Transaction management
│   └── spring-data-*      → Spring Data repositories
│
├── Web
│   ├── spring-web         → Foundational web support
│   ├── spring-webmvc      → Spring MVC (DispatcherServlet)
│   └── spring-webflux     → Reactive web (non-blocking)
│
└── Test
    └── spring-test        → Testing support (@SpringBootTest, MockMvc, …)
```

---

## Request Lifecycle in Spring MVC

Here's exactly what happens when a request hits your Spring Boot app:

```
1. HTTP request arrives at Tomcat (embedded server)
         │
2. Tomcat passes it to the DispatcherServlet
         │                        ┌──────────────────┐
3. DispatcherServlet consults ──▶ │ Handler Mapping  │ → finds which @Controller
         │                        └──────────────────┘   method handles this URL
         │                        ┌──────────────────┐
4. Passes through ──────────────▶ │ Handler Adapter  │ → invokes the method
         │                        └──────────────────┘
         │
5. Your @Controller method runs
   • @RequestBody deserialized (JSON → Java object via Jackson)
   • @Valid validation runs
   • Service layer called
   • Response object returned
         │
6. Response passes through ──────▶ Message Converter → Java object → JSON
         │
7. HTTP response sent back to client
```

**compare to Express:**
```
HTTP → Express app.use() middleware chain → router.get(path, handler) → res.json()
```

Same idea — just more explicit and more automated in Spring.

---

## The TaskForge Package Structure

Here's how TaskForge will be organized:

```
com.taskforge/
├── TaskforgeApplication.java          ← main() entry point
├── config/
│   ├── SecurityConfig.java            ← Spring Security config
│   └── AuditConfig.java               ← Auditing config
├── controller/
│   ├── AuthController.java            ← /api/auth/*
│   ├── ProjectController.java         ← /api/projects/*
│   ├── TaskController.java            ← /api/tasks/*
│   └── CommentController.java         ← /api/comments/*
├── service/
│   ├── AuthService.java
│   ├── ProjectService.java
│   ├── TaskService.java
│   └── CommentService.java
├── repository/
│   ├── UserRepository.java
│   ├── ProjectRepository.java
│   ├── TaskRepository.java
│   └── CommentRepository.java
├── model/
│   ├── User.java
│   ├── Project.java
│   ├── Task.java
│   ├── Comment.java
│   └── enums/
│       ├── Role.java
│       ├── TaskStatus.java
│       └── Priority.java
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── CreateProjectRequest.java
│   │   └── CreateTaskRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── ProjectResponse.java
│       └── TaskResponse.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── UserDetailsServiceImpl.java
└── exception/
    ├── GlobalExceptionHandler.java
    └── ResourceNotFoundException.java
```

---

## Try It Yourself

**Exercise:** Trace a request through the layers.

Look at this request: `GET /api/projects/42`

Write down (or sketch) which class handles it at each layer, and what that class's responsibility is. We haven't built it yet, but try to reason about it from the architecture.

<details>
<summary>Answer</summary>

1. **Tomcat (embedded server)** — receives the HTTP request
2. **DispatcherServlet** — routes to `ProjectController`
3. **`ProjectController.getProjectById(42)`** — extracts `id=42` from path, calls service
4. **`ProjectService.findById(42)`** — applies business rules (e.g., check user is a member), calls repository
5. **`ProjectRepository.findById(42)`** — queries the database via JPA/Hibernate
6. **Database (PostgreSQL)** — returns the project row
7. Response flows back up: Entity → `ProjectResponse` DTO → JSON → HTTP 200
</details>

---

## Capstone Connection

Every file you create in TaskForge belongs in exactly one of the layers above. Before creating any class, ask:
- "Does this class handle HTTP input/output?" → `controller/`
- "Does this class contain business logic?" → `service/`
- "Does this class talk to the database?" → `repository/`
- "Is this a database entity?" → `model/`
