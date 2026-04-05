# 1.6 — Spring IoC Container

## Concept

The **IoC Container** is the heart of Spring. It's the runtime that:
1. Discovers all your Spring-managed classes (called **beans**)
2. Creates instances of them
3. Wires their dependencies together (DI)
4. Manages their entire lifecycle

> **Inversion of Control (IoC)** means: instead of *your* code controlling object creation and wiring, you *invert* that control to the framework.

**Node.js analogy:** If you've used NestJS, the NestJS `DIContainer` is IoC in action. In Express without a framework, *you* are the IoC container — you `require()` things, create instances, and pass them around. Spring replaces that manual work.

---

## BeanFactory vs ApplicationContext

Spring has two container interfaces:

```
BeanFactory  (basic container — rarely used directly)
     │
     └── ApplicationContext  (extended container — what you always use)
              │
              ├── AnnotationConfigApplicationContext     (standalone Java apps)
              ├── ClassPathXmlApplicationContext         (legacy XML config)
              └── AnnotationConfigServletWebServerApplicationContext
                                                         (Spring Boot web apps — this one)
```

In practice, you never instantiate `ApplicationContext` directly. Spring Boot creates and manages it for you. You can access it in beans if you ever need to:

```java
@Service
public class SomeService implements ApplicationContextAware {
    
    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    public void doSomething() {
        // Dynamically retrieve a bean
        TaskRepository repo = context.getBean(TaskRepository.class);
    }
}
```

But you should rarely need to do this — constructor injection is almost always the right approach.

---

## How Beans Are Discovered: Component Scanning

Spring Boot automatically scans your package and all sub-packages for classes annotated with **stereotype annotations** — and registers them as beans:

```
@SpringBootApplication
  │
  └── @ComponentScan   ← Scans com.taskforge.** for annotated classes
```

```java
@SpringBootApplication          // Includes @ComponentScan of the current package
public class TaskforgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskforgeApplication.class, args);
    }
}
```

**Stereotype annotations** (all are specializations of `@Component`):

| Annotation | Layer | Semantics |
|-----------|-------|----------|
| `@Component` | Any | Generic bean — use when no specific layer applies |
| `@Service` | Business layer | Marks a service class (business logic) |
| `@Repository` | Data layer | Marks a DAO/repository; enables DB exception translation |
| `@Controller` | Web layer | Handles HTTP requests (returns views) |
| `@RestController` | Web layer | `@Controller` + `@ResponseBody` — returns JSON/data |
| `@Configuration` | Config | Contains `@Bean` methods — defines beans explicitly |

---

## Explicit Bean Registration with `@Bean`

For third-party classes you don't control (e.g., library objects), use `@Bean` inside a `@Configuration` class:

```java
// Node.js equivalent of manually creating and exporting a shared instance:
// export const jwtEncoder = new JwtEncoder(key);

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Spring registers the returned object as a bean named "passwordEncoder"
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
```

Now `PasswordEncoder` and `ObjectMapper` can be injected anywhere in your app:

```java
@Service
public class AuthService {
    private final PasswordEncoder passwordEncoder;  // Injected from SecurityConfig

    public AuthService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
}
```

---

## Bean Naming

By default, Spring names a bean after its class (lowercase first letter):

```java
@Service
public class ProjectService { ... }
// Bean name: "projectService"

@Service("myCustomName")
public class SomeService { ... }
// Bean name: "myCustomName"
```

You rarely need to know bean names unless you're using `@Qualifier`.

---

## The Bean Lifecycle

Spring manages the full lifecycle of every bean:

```
1. Instantiation   → Spring creates the object (calls constructor)
2. Dependency Injection → Spring injects dependencies
3. @PostConstruct  → Your initialization code runs (optional)
4. Ready           → Bean is available for use
5. Destroy         → Container shuts down
6. @PreDestroy     → Your cleanup code runs (optional)
```

**`@PostConstruct` and `@PreDestroy` in practice:**

```java
@Service
public class CacheService {

    private final Map<String, Object> cache = new HashMap<>();

    @PostConstruct                        // Runs after Spring injects all dependencies
    public void init() {
        System.out.println("CacheService initialized — warming up cache...");
        // Load initial data, connect to external service, etc.
    }

    @PreDestroy                           // Runs before Spring shuts down
    public void cleanup() {
        cache.clear();
        System.out.println("CacheService destroyed — cache cleared.");
    }
}
```

**Node.js equivalent:**
```typescript
// NestJS equivalent
@Injectable()
export class CacheService implements OnModuleInit, OnModuleDestroy {
  onModuleInit() { /* @PostConstruct */ }
  onModuleDestroy() { /* @PreDestroy */ }
}
```

---

## What Happens at Startup

When `SpringApplication.run(TaskforgeApplication.class, args)` is called:

```
1. Create ApplicationContext
2. Scan com.taskforge.** for @Component, @Service, @Repository, @Controller, @Configuration
3. Process @Configuration classes — call all @Bean methods
4. Build dependency graph — determine order to create beans
5. Instantiate beans in dependency order (leaves first)
6. Inject dependencies (constructor injection)
7. Call @PostConstruct methods
8. Start embedded Tomcat server
9. Register @RestController routes with DispatcherServlet
10. Application ready — accepting requests
```

If any dependency is missing (e.g., a bean doesn't exist), Spring fails at **step 6** with `NoSuchBeanDefinitionException` — before the server starts. This is the "fail fast" advantage.

---

## Try It Yourself

**Exercise:** Observe the IoC container in action.

1. Add this to any `@Service` class:
```java
@PostConstruct
public void init() {
    System.out.println(getClass().getSimpleName() + " bean created by Spring");
}
```

2. Start your app and check the logs.

3. Add a bean registered in a `@Configuration` class:
```java
@Configuration
public class AppConfig {
    @Bean
    public String appVersion() {
        return "1.0.0";
    }
}
```

4. Inject `String appVersion` into a `@Service` and print it in `@PostConstruct`. (Hint: you'll need `@Qualifier("appVersion")` since there may be multiple Strings.)

<details>
<summary>Solution for step 4</summary>

```java
@Service
public class InfoService {

    private final String appVersion;

    public InfoService(@Qualifier("appVersion") String appVersion) {
        this.appVersion = appVersion;
    }

    @PostConstruct
    public void init() {
        System.out.println("App version: " + appVersion);
    }
}
```
</details>

---

## Capstone Connection

The IoC container creates and wires every class in TaskForge. You never call `new ProjectService()` — Spring does. This is why you can test services by injecting mocks: you're just taking over the IoC container's job in tests.
