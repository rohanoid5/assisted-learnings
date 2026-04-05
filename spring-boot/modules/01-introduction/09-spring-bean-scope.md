# 1.9 — Spring Bean Scope

## Concept

By default, Spring creates exactly **one instance** of each bean and reuses it everywhere — this is the **Singleton** scope. But Spring supports other scopes for scenarios where you need new instances per request, per session, etc.

**Node.js analogy:** Node.js modules are cached after the first `require()` — effectively singletons. Spring's default singleton behavior is the same. Non-singleton scopes in Spring are like factory functions that return a new object each time.

```javascript
// Node.js module cache = Singleton behavior
const service = require('./projectService'); // Same object every require()
```

```java
// Spring default = Singleton
@Service
public class ProjectService { ... }
// Same instance injected everywhere in the application
```

---

## The Available Scopes

| Scope | When a new instance is created | Use case |
|-------|-------------------------------|----------|
| `singleton` | Once, when the container starts (default) | Almost everything |
| `prototype` | Every time a bean is requested | Stateful objects that shouldn't be shared |
| `request` | Each HTTP request | Request-scoped data (web apps only) |
| `session` | Each HTTP session | Session-scoped state (web apps only) |
| `application` | Once per ServletContext | App-wide state (rarely used) |
| `websocket` | Each WebSocket session | WebSocket-scoped state |

---

## Singleton Scope (Default)

```java
// No @Scope annotation = singleton
@Service
public class ProjectService {
    // One instance shared across the entire application
    // Thread-safe design is YOUR responsibility
}
```

Singleton beans are created at startup and destroyed when the app shuts down.

**Key requirement:** Singleton beans must be **stateless** (or thread-safe). If you store mutable state in fields, multiple concurrent requests will stomp on each other:

```java
@Service
public class BadService {
    // ❌ DANGEROUS — shared mutable state in a singleton
    private User currentUser;  // multiple threads will overwrite this simultaneously

    public void setCurrentUser(User user) {
        this.currentUser = user;  // race condition!
    }
}
```

---

## Prototype Scope

```java
@Component
@Scope("prototype")   // or @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TaskBuilder {

    private final List<String> tags = new ArrayList<>();

    public TaskBuilder addTag(String tag) {
        tags.add(tag);
        return this;
    }

    public Task build() {
        Task task = new Task();
        task.setTags(new ArrayList<>(tags));
        return task;
    }
}
```

Now every injection of `TaskBuilder` gets a **fresh instance**:

```java
@Service
public class TaskService {

    private final ApplicationContext context;  // Needed to get prototype beans

    public TaskService(ApplicationContext context) {
        this.context = context;
    }

    public Task createTask(CreateTaskRequest request) {
        // Each call gets a NEW TaskBuilder instance
        TaskBuilder builder = context.getBean(TaskBuilder.class);
        return builder
            .addTag("feature")
            .addTag(request.getPriority().name())
            .build();
    }
}
```

> **Why use `ApplicationContext` for prototype beans?**
> If you inject a prototype bean directly (`@Autowired TaskBuilder builder`), Spring injects it once at startup — effectively making it a singleton from `TaskService`'s perspective. To get a new instance each time, you request from the context.

A cleaner approach: use `ObjectProvider<T>`:

```java
@Service
public class TaskService {

    private final ObjectProvider<TaskBuilder> taskBuilderProvider;

    public TaskService(ObjectProvider<TaskBuilder> taskBuilderProvider) {
        this.taskBuilderProvider = taskBuilderProvider;
    }

    public Task createTask(CreateTaskRequest request) {
        TaskBuilder builder = taskBuilderProvider.getObject(); // new instance each call
        return builder.addTag("feature").build();
    }
}
```

---

## Request Scope

```java
@Component
@RequestScoped                        // javax.enterprise OR use @Scope("request")
// or:
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {

    private String requestId = UUID.randomUUID().toString();
    private Long authenticatedUserId;

    // getters + setters
    public String getRequestId() { return requestId; }
    public Long getAuthenticatedUserId() { return authenticatedUserId; }
    public void setAuthenticatedUserId(Long id) { this.authenticatedUserId = id; }
}
```

```java
@Service
public class AuditService {

    private final RequestContext requestContext;  // Injected as a proxy

    public AuditService(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public void logAction(String action) {
        // Each HTTP request gets its own RequestContext instance
        log.info("Request {}: {}", requestContext.getRequestId(), action);
    }
}
```

**`proxyMode = ScopedProxyMode.TARGET_CLASS` explained:** Since `AuditService` is a singleton, it can't hold a direct reference to a request-scoped bean (which changes per request). Instead, Spring injects a **proxy** that delegates to the real request-scoped bean on each method call. This is automatic — you just need to specify `proxyMode`.

---

## Practical Guide: Which Scope to Use?

```
Is the bean stateless?
├── YES → Singleton (default). Stop thinking about it.
└── NO → Does the state vary per...
         ├── HTTP request?  → Request scope
         ├── HTTP session?  → Session scope
         └── Individual call? → Prototype scope
```

In practice:
- **99%** of your beans: `singleton`
- **Occasionally**: `prototype` (builder patterns, stateful processors)
- **Rarely**: `request`/`session` (usually you pass state as method params instead)

---

## Checking Singleton Behavior

```java
@SpringBootTest
class SingletonTest {

    @Autowired
    private ProjectService projectService1;

    @Autowired
    private ProjectService projectService2;

    @Test
    void shouldBeSameInstance() {
        // Both references point to the exact same object
        assertThat(projectService1).isSameAs(projectService2);
    }
}
```

---

## Try It Yourself

**Exercise:** Verify scope behavior.

1. Create a `@Component` called `RequestIdGenerator` that generates a UUID in its constructor:
```java
@Component
public class RequestIdGenerator {
    private final String id = UUID.randomUUID().toString();
    public String getId() { return id; }
}
```

2. Inject it into two different services and compare the `getId()` values. They should be **identical** (singleton — same instance).

3. Now add `@Scope("prototype")` to the class and repeat. The IDs should be **different** (new instance each injection).

<details>
<summary>Expected output</summary>

Without `@Scope`: 
```
Service1 ID: a1b2c3d4-...
Service2 ID: a1b2c3d4-...    ← Same UUID
```

With `@Scope("prototype")`:
```
Service1 ID: a1b2c3d4-...
Service2 ID: f5e6d7c8-...    ← Different UUID
```
</details>

---

## Capstone Connection

All TaskForge beans (services, repositories, controllers) use the **default singleton scope**. They're designed to be stateless — no mutable instance fields. All state is passed through method parameters or stored in the database. This is the correct design for a web application.
