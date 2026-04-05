# 04 — REST Controllers

## Building the TaskForge REST API

This is where it all comes together. We'll build the full REST API for TaskForge, connecting controllers to services, with proper validation and error handling.

**What we're building:**
- `POST /api/auth/register` — register a new user
- `POST /api/auth/login` — login, receive JWT
- CRUD for `/api/projects`
- CRUD for `/api/tasks`
- CRUD for `/api/tasks/{taskId}/comments`

---

## Project Setup for Module 3

Make sure your `pom.xml` has Spring Validation (included in `spring-boot-starter-web` via `spring-boot-starter-validation`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

And enable H2 for now (real DB in Module 4):

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Standard Response Structure

Define a consistent API response envelope (optional but professional):

```java
// src/main/java/com/taskforge/dto/response/ApiResponse.java
package com.taskforge.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
```

---

## Auth Controller

```java
// src/main/java/com/taskforge/controller/AuthController.java
package com.taskforge.controller;

import com.taskforge.dto.request.LoginRequest;
import com.taskforge.dto.request.RegisterRequest;
import com.taskforge.dto.response.ApiResponse;
import com.taskforge.dto.response.JwtResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("User registered successfully", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        JwtResponse jwt = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(jwt));
    }
}
```

**Request DTOs:**
```java
// src/main/java/com/taskforge/dto/request/RegisterRequest.java
package com.taskforge.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100)
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}

// src/main/java/com/taskforge/dto/request/LoginRequest.java
public record LoginRequest(
    @NotBlank @Email
    String email,

    @NotBlank
    String password
) {}
```

**Response DTOs:**
```java
// src/main/java/com/taskforge/dto/response/JwtResponse.java
public record JwtResponse(
    String accessToken,
    String tokenType,
    long expiresIn     // seconds
) {
    public JwtResponse(String accessToken) {
        this(accessToken, "Bearer", 86400L);  // 24 hours
    }
}

// src/main/java/com/taskforge/dto/response/UserResponse.java
public record UserResponse(
    Long id,
    String name,
    String email,
    Role role
) {}
```

---

## Project Controller

```java
// src/main/java/com/taskforge/controller/ProjectController.java
package com.taskforge.controller;

import com.taskforge.dto.request.CreateProjectRequest;
import com.taskforge.dto.request.UpdateProjectRequest;
import com.taskforge.dto.response.ApiResponse;
import com.taskforge.dto.response.ProjectResponse;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // GET /api/projects — list all projects the current user is a member of
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getMyProjects(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(projectService.findByMember(currentUser))
        );
    }

    // GET /api/projects/{id} — get a specific project
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(projectService.findById(id, currentUser))
        );
    }

    // POST /api/projects — create a new project
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ProjectResponse created = projectService.create(request, currentUser);
        return ResponseEntity
            .created(URI.create("/api/projects/" + created.id()))
            .body(ApiResponse.ok("Project created", created));
    }

    // PUT /api/projects/{id} — update a project
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(projectService.update(id, request, currentUser))
        );
    }

    // DELETE /api/projects/{id} — delete a project
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        projectService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // POST /api/projects/{id}/members/{userId} — add member
    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> addMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(projectService.addMember(id, userId, currentUser))
        );
    }
}
```

**Project DTOs:**
```java
// src/main/java/com/taskforge/dto/request/CreateProjectRequest.java
public record CreateProjectRequest(
    @NotBlank(message = "Project name is required")
    @Size(max = 200)
    String name,

    @Size(max = 1000)
    String description
) {}

public record UpdateProjectRequest(
    @NotBlank @Size(max = 200)
    String name,
    @Size(max = 1000)
    String description
) {}

// src/main/java/com/taskforge/dto/response/ProjectResponse.java
public record ProjectResponse(
    Long id,
    String name,
    String description,
    UserResponse owner,
    int memberCount,
    int taskCount,
    LocalDateTime createdAt
) {}
```

---

## Task Controller

```java
// src/main/java/com/taskforge/controller/TaskController.java
package com.taskforge.controller;

import com.taskforge.dto.request.CreateTaskRequest;
import com.taskforge.dto.request.UpdateTaskRequest;
import com.taskforge.dto.response.ApiResponse;
import com.taskforge.dto.response.TaskResponse;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // GET /api/tasks?projectId=1&status=TODO&page=0&size=20
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(taskService.findAll(projectId, status, page, size, currentUser))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(taskService.findById(id, currentUser))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        TaskResponse created = taskService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Task created", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(taskService.update(id, request, currentUser))
        );
    }

    // PATCH for partial updates — e.g., just changing status
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,          // or a small record DTO
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(taskService.updateStatus(id, status, currentUser))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        taskService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
```

**Task DTOs:**
```java
// src/main/java/com/taskforge/dto/request/CreateTaskRequest.java
public record CreateTaskRequest(
    @NotBlank(message = "Task title is required")
    @Size(max = 200)
    String title,

    @Size(max = 2000)
    String description,

    @NotNull(message = "Project ID is required")
    Long projectId,

    Priority priority,     // defaults to MEDIUM in service
    Long assigneeId        // optional
) {}

public record UpdateTaskRequest(
    @NotBlank @Size(max = 200)
    String title,
    @Size(max = 2000)
    String description,
    TaskStatus status,
    Priority priority,
    Long assigneeId
) {}

// src/main/java/com/taskforge/dto/response/TaskResponse.java
public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    Long projectId,
    String projectName,
    UserResponse assignee,
    UserResponse createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

---

## Comment Controller

```java
// src/main/java/com/taskforge/controller/CommentController.java
package com.taskforge.controller;

import com.taskforge.dto.request.CreateCommentRequest;
import com.taskforge.dto.response.ApiResponse;
import com.taskforge.dto.response.CommentResponse;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Note: nested under /api/tasks/{taskId}/comments
@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            ApiResponse.ok(commentService.findByTask(taskId, currentUser))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        CommentResponse created = commentService.create(taskId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Comment added", created));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long taskId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        commentService.delete(taskId, commentId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
```

---

## Global Exception Handler

```java
// src/main/java/com/taskforge/exception/GlobalExceptionHandler.java
package com.taskforge.exception;

import com.taskforge.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value",
                (first, second) -> first
            ));

        Map<String, Object> body = Map.of(
            "success", false,
            "message", "Validation failed",
            "errors", fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericError(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"));
    }
}
```

```java
// src/main/java/com/taskforge/exception/ResourceNotFoundException.java
package com.taskforge.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

---

## Bean Validation Constraints Reference

| Annotation | Works on | Description |
|-----------|----------|-------------|
| `@NotNull` | Any | Must not be null |
| `@NotBlank` | String | Not null, not empty, not whitespace |
| `@NotEmpty` | String, Collection | Not null, not empty |
| `@Size(min, max)` | String, Collection | Length constraints |
| `@Min(value)` / `@Max(value)` | Numbers | Numeric range |
| `@Email` | String | Valid email format |
| `@Pattern(regexp)` | String | Regex match |
| `@Positive` | Numbers | > 0 |
| `@PositiveOrZero` | Numbers | >= 0 |
| `@Future` / `@Past` | Date/time | Must be in future/past |
| `@Valid` | Object | Cascade validation to nested objects |

**Custom Validator example:**
```java
@Documented
@Constraint(validatedBy = UniqueEmailValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueEmail {
    String message() default "Email already in use";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    @Autowired private UserRepository userRepository;

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email != null && !userRepository.existsByEmail(email);
    }
}
```

---

## Testing Your Controllers (Preview)

Until Module 5 (Security) is done, you can temporarily disable security for testing. Add to `application-dev.yml`:

```yaml
spring:
  security:
    user:
      name: testuser
      password: testpass
```

Then use curl or HTTPie:

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}' | jq

# Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' | jq

# Create project (with JWT from login response)
TOKEN="<paste token here>"
curl -s -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"My First Project","description":"Learning Spring Boot"}' | jq

# Create task
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Set up project","projectId":1,"priority":"HIGH"}' | jq

# Test validation error
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"","projectId":1}' | jq
# → 400 Bad Request with field errors
```

---

## Capstone Connection

This module completes the **web layer** of TaskForge. After Module 4 (Spring Data), these controllers will have real database persistence. After Module 5 (Spring Security), the `@AuthenticationPrincipal UserPrincipal` parameters will be populated from JWT tokens.

The pattern established here — Controller → Service → Repository, with DTOs at every boundary — is the foundation all remaining modules build on.

**Next module:** [Module 4 — Spring Data](../../04-spring-data/README.md) — JPA entities, Hibernate relationships, Spring Data repositories, and real database queries.
