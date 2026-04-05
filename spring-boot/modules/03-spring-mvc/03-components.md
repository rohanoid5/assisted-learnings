# 03 — Spring MVC Components

## The Layered Architecture in Practice

You saw the theory in Module 1. Here we go deeper on how each layer looks and communicates in a real REST API.

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                         │
│              @RestController (HTTP interface)                 │
│   Handles: routing, request parsing, response serialization  │
├─────────────────────────────────────────────────────────────┤
│                    Business Layer                             │
│                   @Service (logic)                            │
│   Handles: business rules, orchestration, transactions       │
├─────────────────────────────────────────────────────────────┤
│                   Persistence Layer                           │
│              @Repository / JpaRepository                      │
│   Handles: DB queries, entity mapping                        │
├─────────────────────────────────────────────────────────────┤
│                    Database Layer                             │
│              PostgreSQL / H2 (Module 4)                      │
└─────────────────────────────────────────────────────────────┘
```

**Node.js equiv (NestJS):** Controller → Service → Repository pattern is identical.

---

## The Controller Layer

### Responsibilities
- Accept HTTP requests
- Parse and validate input (parameters, headers, body)
- Call the appropriate Service
- Return an appropriate HTTP response

### What it should NOT do
- Contain business logic
- Call repositories directly
- Access the database

```java
// ✅ Good Controller
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(taskService.findAll(currentUser));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        TaskResponse created = taskService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

```java
// ❌ Bad Controller — business logic leaked in
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository; // ← calling repository directly

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        if (task.getTitle() == null || task.getTitle().isBlank()) {
            throw new RuntimeException("Title required"); // ← business validation here
        }
        task.setCreatedAt(LocalDateTime.now());           // ← business logic here
        return taskRepository.save(task);                 // ← raw entity returned
    }
}
```

---

## The Service Layer

### Responsibilities
- Implement business logic
- Orchestrate multiple repositories or external calls
- Apply `@Transactional` boundaries
- Map between entities and DTOs

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // default: all methods read-only
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<TaskResponse> findAll(UserPrincipal currentUser) {
        return taskRepository.findByProjectMember(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional  // overrides readOnly=true for write operations
    public TaskResponse create(CreateTaskRequest request, UserPrincipal currentUser) {
        // 1. Validate business rules
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        // 2. Check permissions (business rule, not security rule)
        if (!project.isMember(currentUser.getId())) {
            throw new AccessDeniedException("You are not a member of this project");
        }

        // 3. Build entity
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .status(TaskStatus.TODO)
                .priority(request.priority())
                .project(project)
                .createdBy(userRepository.getReferenceById(currentUser.getId()))
                .build();

        // 4. Persist
        Task saved = taskRepository.save(task);

        // 5. Return DTO (never return raw entity from Service)
        return toResponse(saved);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getPriority(),
            task.getProject().getId(),
            task.getProject().getName()
        );
    }
}
```

### Why @Transactional(readOnly = true)?

Puts a performance hint on the DB connection — the DB driver can skip dirty checking and use optimistic read locks. Good default for service classes.

---

## The Repository Layer

In Module 4 (Spring Data), you'll go deep. For now, here's the pattern:

```java
// JpaRepository<EntityType, IdType>
// Spring Data generates the implementation at runtime — never write SQL for basic CRUD
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Spring Data derives the query from the method name:
    List<Task> findByProjectId(Long projectId);
    List<Task> findByStatus(TaskStatus status);
    List<Task> findByAssigneeId(Long userId);

    // For complex queries, use @Query with JPQL
    @Query("SELECT t FROM Task t JOIN t.project.members m WHERE m.id = :userId")
    List<Task> findByProjectMember(@Param("userId") Long userId);
}
```

**Node.js equiv (TypeORM):**
```typescript
// What you'd write in TypeORM
const tasks = await taskRepository.findBy({ project: { id: projectId } });
// Spring Data equivalent:
// List<Task> findByProjectId(Long projectId);
```

---

## DTOs: Request and Response Objects

Never expose JPA entities directly through your API. Use DTOs (Data Transfer Objects).

**Why?**
1. Entities can have circular references (`Task → Project → List<Task>`) that break Jackson serialization
2. Entities expose every DB column — you might not want to return password hashes, metadata, etc.
3. API contract is separate from DB schema — you can change one without breaking the other
4. Validation annotations on request DTOs don't pollute your entity model

### Java Records for DTOs (Java 16+, recommended)

```java
// Request DTOs — what comes IN
public record CreateTaskRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 200)
    String title,

    String description,

    @NotNull
    Long projectId,

    Priority priority
) {}

public record UpdateTaskRequest(
    @NotBlank
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    Long assigneeId
) {}

// Response DTOs — what goes OUT
public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    Long projectId,
    String projectName
) {}

public record TaskDetailResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    Long projectId,
    String projectName,
    UserSummary assignee,
    UserSummary createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<CommentResponse> comments
) {}
```

Java **records** are immutable, auto-generate `equals()`, `hashCode()`, `toString()`, and a canonical constructor. Perfect for DTOs.

**Node.js equiv:** TypeScript interface or Zod schema for validation + type safety.

---

## @RequestMapping and HTTP Method Annotations

```java
@RestController
@RequestMapping("/api/tasks")       // ← base path for all methods in this controller
public class TaskController {

    @GetMapping               // GET /api/tasks
    @GetMapping("/{id}")      // GET /api/tasks/42
    @PostMapping              // POST /api/tasks
    @PutMapping("/{id}")      // PUT /api/tasks/42
    @PatchMapping("/{id}")    // PATCH /api/tasks/42
    @DeleteMapping("/{id}")   // DELETE /api/tasks/42
}
```

These are shortcuts for `@RequestMapping(method = RequestMethod.GET)`, etc.

---

## Binding Request Data: Full Reference

```java
@GetMapping("/{id}")
public TaskResponse getById(
    @PathVariable Long id        // from URL path: /tasks/42 → id=42
) { ... }

@GetMapping
public List<TaskResponse> search(
    @RequestParam String status,                      // ?status=TODO (required)
    @RequestParam(required = false) String priority,  // ?priority=HIGH (optional)
    @RequestParam(defaultValue = "0") int page,       // ?page=0 (has default)
    @RequestParam(defaultValue = "20") int size       // ?size=20
) { ... }

@PostMapping
public ResponseEntity<TaskResponse> create(
    @Valid @RequestBody CreateTaskRequest request, // JSON body → DTO, @Valid triggers validation
    @RequestHeader("Authorization") String authHeader  // HTTP header
) { ... }

// Spring Security adds this parameter resolver:
@GetMapping("/my-tasks")
public List<TaskResponse> myTasks(
    @AuthenticationPrincipal UserPrincipal currentUser  // from JWT security context
) { ... }
```

---

## ResponseEntity: Control Your HTTP Response

`ResponseEntity<T>` gives you full control over status code, headers, and body:

```java
// Simple: 200 OK + body
return ResponseEntity.ok(taskResponse);

// Created: 201 Created + Location header + body
return ResponseEntity
    .created(URI.create("/api/tasks/" + saved.getId()))
    .body(taskResponse);

// No content: 204 No Content (common for DELETE)
return ResponseEntity.noContent().build();

// Not found: 404
return ResponseEntity.notFound().build();

// Custom status code
return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
```

**Node.js equiv:**
```javascript
res.status(201).json(task)        // ResponseEntity.status(CREATED).body(task)
res.status(204).send()            // ResponseEntity.noContent().build()
res.status(409).json(errorBody)   // ResponseEntity.status(CONFLICT).body(errorBody)
```

---

## @ControllerAdvice — Global Exception Handling

This is Spring's equivalent of Express error middleware: `(err, req, res, next) => {}`.

```java
@RestControllerAdvice  // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    // Handle our custom exception
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            "NOT_FOUND",
            ex.getMessage(),
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Handle @Valid validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage,
                (first, second) -> first  // keep first error per field
            ));

        return ResponseEntity.badRequest().body(
            new ValidationErrorResponse("VALIDATION_FAILED", fieldErrors)
        );
    }

    // Handle access denied
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", "Access denied", Instant.now()));
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        // Log it — don't expose internal error details to clients
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", Instant.now()));
    }
}
```

**Error response DTOs:**
```java
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp
) {}

public record ValidationErrorResponse(
    String code,
    Map<String, String> fieldErrors
) {}
```

**Custom exception:**
```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
```

---

## CORS Configuration

Cross-Origin Resource Sharing — needed when your React/Vue frontend runs on a different port than the Spring Boot API.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000", "https://taskforge.io")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

Or use `@CrossOrigin` on individual controllers/methods:
```java
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/tasks")
public class TaskController { ... }
```

**Node.js equiv:**
```javascript
app.use(cors({ origin: 'http://localhost:3000', credentials: true }));
```

---

## Capstone Connection

In TaskForge, the MVC layer looks like this:

```
modules/03-spring-mvc/exercises/
└── Full CRUD for Projects and Tasks

capstone/taskforge/src/main/java/com/taskforge/
├── controller/
│   ├── AuthController.java       ← @PostMapping /api/auth/login, /register
│   ├── ProjectController.java    ← @RestController /api/projects
│   ├── TaskController.java       ← @RestController /api/tasks
│   └── CommentController.java    ← @RestController /api/tasks/{taskId}/comments
├── service/
│   ├── AuthService.java
│   ├── ProjectService.java
│   ├── TaskService.java
│   └── CommentService.java
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── CreateProjectRequest.java
│   │   ├── CreateTaskRequest.java
│   │   └── CreateCommentRequest.java
│   └── response/
│       ├── JwtResponse.java
│       ├── ProjectResponse.java
│       ├── TaskResponse.java
│       └── CommentResponse.java
└── exception/
    ├── ResourceNotFoundException.java
    └── GlobalExceptionHandler.java
```

**Next:** [04 — REST Controllers](./04-rest-controllers.md) — building the full TaskForge API.
