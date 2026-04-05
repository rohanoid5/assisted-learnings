# 1.8 — Spring Annotations

## Concept

Annotations are Java's equivalent of TypeScript/NestJS decorators — metadata you attach to classes, methods, and fields that Spring reads at startup to decide how to handle them.

**TypeScript decorator → Java annotation:**
```typescript
// NestJS
@Controller('tasks')
@UseGuards(AuthGuard)
export class TaskController {
    @Get(':id')
    findOne(@Param('id') id: string) { ... }
}
```

```java
// Spring Boot
@RestController
@RequestMapping("/tasks")
@PreAuthorize("isAuthenticated()")
public class TaskController {
    @GetMapping("/{id}")
    public Task findOne(@PathVariable Long id) { ... }
}
```

Same idea — different syntax, same purpose.

---

## Core Annotation Reference

### Stereotype Annotations (Bean Registration)

| Annotation | Purpose | Node.js Equivalent |
|-----------|---------|------------------|
| `@Component` | Generic Spring-managed bean | `@Injectable()` (NestJS generic) |
| `@Service` | Business layer bean | `@Injectable()` in a service |
| `@Repository` | Persistence layer bean | `@Injectable()` in a repository |
| `@Controller` | Web layer — returns views | Express router module |
| `@RestController` | Web layer — returns data (JSON) | `@Controller()` (NestJS) |
| `@Configuration` | Configuration class with `@Bean` methods | A NestJS module with providers |

### Configuration Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Bean` | Declares a method return value as a Spring bean (inside `@Configuration`) |
| `@Value("${key}")` | Injects a single config value from `application.yml` |
| `@ConfigurationProperties(prefix = "app")` | Binds a YAML subtree to a class |
| `@Profile("dev")` | Activates a bean only for a specific profile |
| `@Conditional(...)` | Activates a bean only when a condition is met |
| `@EnableConfigurationProperties` | Enables `@ConfigurationProperties` scanning |

### Web / MVC Annotations

| Annotation | Purpose |
|-----------|---------|
| `@RequestMapping("/api/v1")` | Base URL mapping on a controller class |
| `@GetMapping("/{id}")` | Maps GET requests |
| `@PostMapping` | Maps POST requests |
| `@PutMapping("/{id}")` | Maps PUT requests |
| `@DeleteMapping("/{id}")` | Maps DELETE requests |
| `@PatchMapping("/{id}")` | Maps PATCH requests |
| `@PathVariable` | Binds `{id}` from the URL |
| `@RequestParam("page")` | Binds `?page=2` query parameter |
| `@RequestBody` | Deserializes the JSON request body |
| `@ResponseBody` | Serializes return value to JSON response |
| `@ResponseStatus(HttpStatus.CREATED)` | Sets the HTTP status code |
| `@Valid` | Triggers Bean Validation on the annotated parameter |
| `@ControllerAdvice` | Global exception handler for all controllers |
| `@ExceptionHandler(ResourceNotFoundException.class)` | Handles a specific exception |
| `@CrossOrigin` | Enables CORS for a controller or method |

### Data / Persistence Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Entity` | Marks a class as a JPA entity (database table) |
| `@Table(name = "tasks")` | Customizes the table name |
| `@Id` | Marks the primary key field |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Auto-increment PK |
| `@Column(nullable = false, length = 255)` | Customizes column properties |
| `@OneToMany`, `@ManyToOne`, `@ManyToMany` | Relationship mappings |
| `@JoinColumn`, `@JoinTable` | Configures join column/table for relationships |
| `@Transactional` | Wraps method in a database transaction |
| `@Query("JPQL or SQL")` | Custom database query on a repository method |
| `@CreatedDate`, `@LastModifiedDate` | Auto-populate timestamps (with JPA auditing) |

### Security Annotations

| Annotation | Purpose |
|-----------|---------|
| `@EnableWebSecurity` | Enables Spring Security |
| `@EnableMethodSecurity` | Enables method-level security annotations |
| `@PreAuthorize("hasRole('ADMIN')")` | Checks authorization before method runs |
| `@PostAuthorize("returnObject.owner == authentication.name")` | Checks after method returns |
| `@Secured("ROLE_ADMIN")` | Simpler role-based security (less flexible than @PreAuthorize) |

### Testing Annotations

| Annotation | Purpose | Jest Equivalent |
|-----------|---------|----------------|
| `@SpringBootTest` | Loads full application context | `describe` with real app running |
| `@WebMvcTest(TaskController.class)` | Loads only web layer | Testing a route handler in isolation |
| `@DataJpaTest` | Loads only JPA/database layer | Testing a model/repository |
| `@MockBean` | Creates a mock and registers it as a bean | `jest.mock('./SomeService')` |
| `@Test` | Marks a test method | `it('should ...', () => {})` |
| `@BeforeEach` | Runs before each test | `beforeEach(() => {})` |

### AOP Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Aspect` | Marks a class as an AOP aspect |
| `@Pointcut` | Defines a reusable pointcut expression |
| `@Before` | Runs before matched method |
| `@After` | Runs after matched method |
| `@Around` | Wraps matched method |
| `@AfterReturning` | Runs after successful return |
| `@AfterThrowing` | Runs after exception |

### Utility Annotations

| Annotation | Purpose |
|-----------|---------|
| `@PostConstruct` | Runs after bean is fully constructed |
| `@PreDestroy` | Runs before bean is destroyed |
| `@Scheduled(cron = "0 0 * * * *")` | Schedules a method to run periodically |
| `@Async` | Runs method asynchronously in a thread pool |
| `@Cacheable("projects")` | Caches method result |
| `@CacheEvict("projects")` | Clears the cache |
| `@Validated` | Enables validation on a class |

---

## Meta-Annotations: What `@SpringBootApplication` Actually Is

```java
@SpringBootApplication  // This one annotation is actually 3 annotations combined:
```

```java
@SpringBootConfiguration    // = @Configuration — this class can declare @Bean methods
@EnableAutoConfiguration    // = Turn on autoconfiguration magic
@ComponentScan              // = Scan current package + sub-packages for components
```

You can verify this by Ctrl+clicking `@SpringBootApplication` in IntelliJ — it opens the annotation definition.

---

## Custom Annotations

You can create your own annotations by combining existing ones:

```java
// Define
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {}

// Use
@RestController
public class AdminController {

    @GetMapping("/admin/users")
    @AdminOnly      // Expands to @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() { ... }
}
```

---

## Try It Yourself

**Exercise:** Identify annotations in the wild.

Look at the Spring Boot auto-generated `TaskforgeApplication.java`:

```java
@SpringBootApplication
public class TaskforgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskforgeApplication.class, args);
    }
}
```

Answer these questions:
1. What three annotations does `@SpringBootApplication` expand to?
2. Which one triggers component scanning?
3. What would happen if you moved `TaskforgeApplication.java` to `com.taskforge.app` (not the root package)? Would `com.taskforge.service.ProjectService` still be found?

<details>
<summary>Answers</summary>

1. `@SpringBootConfiguration`, `@EnableAutoConfiguration`, `@ComponentScan`
2. `@ComponentScan`
3. No! `@ComponentScan` scans the package of the annotated class and its sub-packages. If `TaskforgeApplication` is in `com.taskforge.app`, it scans `com.taskforge.app.**` — not `com.taskforge.service.**`. Fix: `@ComponentScan(basePackages = "com.taskforge")` or move `TaskforgeApplication` back to the root package.
</details>

---

## Capstone Connection

You'll use virtually every annotation in this reference table throughout TaskForge. When you encounter an unfamiliar annotation, come back to this file — it covers the full set you'll need.
