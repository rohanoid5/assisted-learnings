# Module 3 — Spring MVC Exercises

## Overview

These exercises build the web layer of TaskForge. By the end, you'll have a running REST API with proper controllers, DTOs, validation, and error handling.

> **Note:** At this stage, we use stub Services that return hardcoded data. Real persistence comes in Module 4.

---

## Exercise 1 — Stub Services + First Controller

**Goal:** Get a working `GET /api/tasks` endpoint returning mocked data.

**Step 1:** Create the TaskStatus and Priority enums:

```java
// src/main/java/com/taskforge/domain/TaskStatus.java
package com.taskforge.domain;

public enum TaskStatus {
    TODO, IN_PROGRESS, REVIEW, DONE
}

// src/main/java/com/taskforge/domain/Priority.java
package com.taskforge.domain;

public enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

// src/main/java/com/taskforge/domain/Role.java
package com.taskforge.domain;

public enum Role {
    ADMIN, MANAGER, USER
}
```

**Step 2:** Create the DTOs:

```java
// src/main/java/com/taskforge/dto/response/TaskResponse.java
package com.taskforge.dto.response;

import com.taskforge.domain.Priority;
import com.taskforge.domain.TaskStatus;
import java.time.LocalDateTime;

public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    Long projectId,
    String projectName,
    LocalDateTime createdAt
) {}
```

**Step 3:** Create a stub TaskService:

```java
// src/main/java/com/taskforge/service/TaskService.java
package com.taskforge.service;

import com.taskforge.domain.Priority;
import com.taskforge.domain.TaskStatus;
import com.taskforge.dto.response.TaskResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {

    // Stub — replace with real DB queries in Module 4
    public List<TaskResponse> findAll(Long projectId) {
        return List.of(
            new TaskResponse(1L, "Set up Spring Boot", "Initialize project", 
                TaskStatus.DONE, Priority.HIGH, projectId, "My Project", LocalDateTime.now().minusDays(2)),
            new TaskResponse(2L, "Create Task entity", "JPA entity + repository",
                TaskStatus.IN_PROGRESS, Priority.HIGH, projectId, "My Project", LocalDateTime.now().minusDays(1)),
            new TaskResponse(3L, "Implement REST API", "Controllers + DTOs",
                TaskStatus.TODO, Priority.MEDIUM, projectId, "My Project", LocalDateTime.now())
        );
    }
}
```

**Step 4:** Create the TaskController with just GET:

```java
// src/main/java/com/taskforge/controller/TaskController.java
package com.taskforge.controller;

import com.taskforge.dto.response.TaskResponse;
import com.taskforge.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(
            @RequestParam(defaultValue = "1") Long projectId) {
        return ResponseEntity.ok(taskService.findAll(projectId));
    }
}
```

**Test it:**
```bash
mvn spring-boot:run
curl -s http://localhost:8080/api/tasks?projectId=1 | jq
```

You should see a JSON array of 3 tasks.

---

## Exercise 2 — Add Full CRUD to TaskController

Expand your TaskController and TaskService with all CRUD operations:

```java
// Add to TaskService — in-memory store (replace with JPA in Module 4)
@Service
public class TaskService {
    
    private final Map<Long, TaskResponse> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1L);

    public List<TaskResponse> findAll(Long projectId) {
        return store.values().stream()
            .filter(t -> t.projectId().equals(projectId))
            .toList();
    }

    public TaskResponse findById(Long id) {
        TaskResponse task = store.get(id);
        if (task == null) throw new ResourceNotFoundException("Task", id);
        return task;
    }

    public TaskResponse create(CreateTaskRequest request) {
        Long id = idSequence.getAndIncrement();
        TaskResponse task = new TaskResponse(
            id,
            request.title(),
            request.description(),
            TaskStatus.TODO,
            request.priority() != null ? request.priority() : Priority.MEDIUM,
            request.projectId(),
            "Project " + request.projectId(),
            LocalDateTime.now()
        );
        store.put(id, task);
        return task;
    }

    public void delete(Long id) {
        if (!store.containsKey(id)) throw new ResourceNotFoundException("Task", id);
        store.remove(id);
    }
}
```

Add to TaskController:
```java
@GetMapping("/{id}")
public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
    return ResponseEntity.ok(taskService.findById(id));
}

@PostMapping
public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
    TaskResponse created = taskService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
    taskService.delete(id);
    return ResponseEntity.noContent().build();
}
```

Add the request DTO:
```java
// src/main/java/com/taskforge/dto/request/CreateTaskRequest.java
public record CreateTaskRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 200)
    String title,
    String description,
    @NotNull(message = "Project ID is required")
    Long projectId,
    Priority priority
) {}
```

**Test:**
```bash
# Create
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Write tests","projectId":1,"priority":"HIGH"}' | jq

# Get by ID
curl -s http://localhost:8080/api/tasks/1 | jq

# Delete
curl -s -X DELETE http://localhost:8080/api/tasks/1
curl -s http://localhost:8080/api/tasks/1
# → Should get ResourceNotFoundException (but no handler yet)
```

---

## Exercise 3 — Add Validation and Error Handling

**Step 1:** Add the exception class:

```java
// src/main/java/com/taskforge/exception/ResourceNotFoundException.java
package com.taskforge.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
```

**Step 2:** Add the global exception handler:

```java
// src/main/java/com/taskforge/exception/GlobalExceptionHandler.java
package com.taskforge.exception;

import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid",
                (a, b) -> a
            ));
        return ResponseEntity.badRequest().body(Map.of(
            "message", "Validation failed",
            "errors", errors
        ));
    }
}
```

**Test all error cases:**
```bash
# Test validation error (blank title)
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"","projectId":1}' | jq
# → {"message":"Validation failed","errors":{"title":"Title is required"}}

# Test not found
curl -s http://localhost:8080/api/tasks/9999 | jq
# → {"error":"Task not found with id: 9999"}

# Test missing required field (null projectId)
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"My Task"}' | jq
# → validation error on projectId
```

---

## Exercise 4 — Add a ProjectController

Build a fully working ProjectController following the same pattern:

```java
// You should have:
// - GET  /api/projects       → list all projects
// - GET  /api/projects/{id}  → get one project
// - POST /api/projects       → create project
// - PUT  /api/projects/{id}  → update project
// - DELETE /api/projects/{id} → delete project

// Create these files yourself, referring to 04-rest-controllers.md:
// - dto/request/CreateProjectRequest.java
// - dto/request/UpdateProjectRequest.java
// - dto/response/ProjectResponse.java
// - service/ProjectService.java
// - controller/ProjectController.java
```

**Hint for ProjectResponse:**
```java
public record ProjectResponse(
    Long id,
    String name,
    String description,
    LocalDateTime createdAt
) {}
```

**Self-check:** Can you hit all 5 endpoints with curl and get expected responses?

---

## Exercise 5 — Nested Resources: Comment Controller

Build a CommentController with nested URL structure:

- `GET  /api/tasks/{taskId}/comments`
- `POST /api/tasks/{taskId}/comments`

**CommentResponse DTO:**
```java
public record CommentResponse(
    Long id,
    String content,
    Long taskId,
    String authorName,
    LocalDateTime createdAt
) {}
```

**CreateCommentRequest DTO:**
```java
public record CreateCommentRequest(
    @NotBlank(message = "Comment cannot be blank")
    @Size(max = 1000)
    String content
) {}
```

Remember: the taskId comes from the **path**, not the request body.

---

## Module 3 Capstone Checkpoint

When you've completed all exercises, verify:

- [ ] `GET /api/tasks?projectId=1` returns a list of tasks
- [ ] `POST /api/tasks` with valid body returns 201 Created
- [ ] `POST /api/tasks` with blank title returns 400 with field errors
- [ ] `GET /api/tasks/9999` returns 404 with error message
- [ ] `DELETE /api/tasks/{id}` returns 204 No Content
- [ ] All 5 project endpoints work
- [ ] Comment endpoints work with nested path `/api/tasks/{taskId}/comments`
- [ ] `GlobalExceptionHandler` is the only place error responses are constructed

**What you've built:**
A complete REST API web layer with proper HTTP semantics, DTO pattern, Bean Validation, and global error handling — the foundation TaskForge will run on.

**Next:** [Module 4 — Spring Data](../../04-spring-data/README.md) — replace the in-memory stores with real PostgreSQL persistence.
