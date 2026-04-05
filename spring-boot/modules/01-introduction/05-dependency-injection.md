# 1.5 — Dependency Injection

## Concept

Dependency Injection (DI) is the core design pattern of Spring. If you've used NestJS, you already know exactly what this is — it's just spelled differently.

**The idea:** Instead of a class creating its own dependencies with `new`, the dependencies are *provided* (injected) from the outside. The Spring IoC container handles the "providing" part automatically.

**Why this matters:**
- Classes are easier to test (you can inject mock dependencies in tests)
- Classes are loosely coupled (they don't know where their dependencies come from)
- Object creation and lifecycle is managed in one place

---

## Understanding DI Through Analogy

**Without DI (tight coupling):**

```typescript
// Node.js — bad pattern
class ProjectService {
  private db: Database;

  constructor() {
    // ProjectService creates and owns its dependency
    this.db = new PostgresDatabase('postgresql://localhost:5432/taskforge');
    //                             ↑ hardwired configuration
    //                             ↑ impossible to swap or mock in tests
  }
}
```

**With DI (loose coupling):**

```typescript
// Node.js with DI (NestJS style)
@Injectable()
class ProjectService {
  // ProjectService RECEIVES its dependency — doesn't create it
  constructor(private readonly projectRepository: ProjectRepository) {}
  //          ↑ NestJS will inject this automatically
}
```

Spring Boot works exactly the same way — just with Java syntax:

```java
// Spring Boot
@Service
public class ProjectService {
    
    private final ProjectRepository projectRepository;

    // Spring injects ProjectRepository automatically
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }
}
```

---

## Three Types of Injection

### 1. Constructor Injection (Recommended ✅)

```java
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    // Spring calls this constructor and provides all dependencies
    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }
}
```

**Why constructor injection is best:**
- Dependencies are `final` — cannot be accidentally reassigned
- The class is impossible to instantiate without its dependencies (fail-fast)
- Works great with unit testing (just call `new TaskService(mockRepo, ...)`)
- No Spring annotations needed on the constructor (Spring detects single constructors automatically)

With Java's **Lombok** library (common in Spring Boot projects), you can eliminate the constructor boilerplate entirely:

```java
@Service
@RequiredArgsConstructor          // Lombok generates the constructor
public class TaskService {

    private final TaskRepository taskRepository;     // Lombok makes fields final
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    // Constructor is auto-generated — no code needed
}
```

### 2. Field Injection (Common but discouraged ⚠️)

```java
@Service
public class TaskService {

    @Autowired                               // Spring injects directly into the field
    private TaskRepository taskRepository;   // NOT final — can be null in tests

    // Problem: can't unit test without starting Spring context
    // Problem: hides dependencies — no explicit constructor
}
```

You'll see `@Autowired` a lot in older Spring Boot code. Prefer constructor injection for new code.

### 3. Setter Injection (Rare use case)

```java
@Service
public class TaskService {

    private TaskRepository taskRepository;

    @Autowired
    public void setTaskRepository(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
    // Use for optional dependencies — otherwise prefer constructor injection
}
```

---

## How Spring Finds What to Inject

When Spring sees `TaskService` needs a `TaskRepository`, it looks in the IoC container for a bean that matches the `TaskRepository` type:

```java
// Spring finds this because @Repository is a @Component alias
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {}
//               ↑ Spring Data auto-creates an implementation at runtime
//               ↑ Spring registers that implementation as a bean
```

If there are **multiple beans** of the same type, Spring can't decide — it throws `NoUniqueBeanDefinitionException`. Fix with `@Qualifier`:

```java
@Service
public class NotificationService {

    private final MessageSender messageSender;

    public NotificationService(@Qualifier("emailSender") MessageSender messageSender) {
        this.messageSender = messageSender;
    }
}
```

Or mark one bean as the default with `@Primary`:
```java
@Component
@Primary
public class EmailSender implements MessageSender { ... }

@Component
public class SmsSender implements MessageSender { ... }
// EmailSender wins by default; SmsSender available via @Qualifier("smsSender")
```

---

## @Autowired Is Often Optional

In Spring 4.3+, if a class has exactly **one constructor**, Spring injects automatically — no `@Autowired` needed:

```java
@Service
public class ProjectService {

    private final ProjectRepository repo;

    // No @Autowired needed — Spring sees one constructor, injects automatically
    public ProjectService(ProjectRepository repo) {
        this.repo = repo;
    }
}
```

---

## Code Example: The TaskForge Service Chain

Here's how DI chains together in TaskForge:

```java
// Layer 3: Repository (talks to database)
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByAssigneeId(Long userId);
}

// Layer 2: Service (business logic — injects repository)
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, 
                       ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    public Task createTask(CreateTaskRequest request, Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setProject(project);
        task.setStatus(TaskStatus.TODO);
        
        return taskRepository.save(task);
    }
}

// Layer 1: Controller (handles HTTP — injects service)
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;   // Injected by Spring

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(@PathVariable Long projectId,
                                           @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(request, projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }
}
```

Spring wires all of this together at startup — you never call `new TaskService(...)` or `new TaskController(...)` anywhere.

---

## Try It Yourself

**Exercise:** Create a `GreetingService` and inject it into a simple `GreetingController`.

1. Create `GreetingService.java` in a `service/` package:
```java
@Service
public class GreetingService {
    public String greet(String name) {
        return "Hello, " + name + "! Welcome to TaskForge.";
    }
}
```

2. Create `GreetingController.java` in a `controller/` package with **constructor injection**:
```java
@RestController
@RequestMapping("/api/greet")
public class GreetingController {
    // Inject GreetingService here via constructor
    // Add a GET endpoint that returns greetingService.greet("World")
}
```

3. Start the app and call `GET http://localhost:8080/api/greet`

<details>
<summary>Solution</summary>

```java
@RestController
@RequestMapping("/api/greet")
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping
    public String greet() {
        return greetingService.greet("World");
    }
}
```

Response: `Hello, World! Welcome to TaskForge.`
</details>

---

## Capstone Connection

Every class in TaskForge follows constructor injection:
- `ProjectController` injects `ProjectService`
- `ProjectService` injects `ProjectRepository` and `UserRepository`
- `AuthService` injects `UserRepository`, `PasswordEncoder`, and `JwtTokenProvider`

This chain is wired automatically by Spring — you'll never call `new` on any of these classes.
