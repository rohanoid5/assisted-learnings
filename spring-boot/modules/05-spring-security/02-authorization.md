# 02 — Authorization

## Authentication vs Authorization

- **Authentication:** "Who are you?" (Module 5, File 01) ✅
- **Authorization:** "Are you allowed to do this?" (this file)

Spring Security supports two levels of authorization:

1. **URL-based** — configured in `SecurityFilterChain` (`authorizeHttpRequests`)
2. **Method-based** — `@PreAuthorize`, `@PostAuthorize`, `@Secured` on service/controller methods

---

## URL-Based Authorization

You already saw this in `SecurityConfig`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
```

**Common matchers:**

| Method | Meaning |
|--------|---------|
| `.permitAll()` | Allow anyone (authenticated or not) |
| `.authenticated()` | Must be logged in |
| `.hasRole("ADMIN")` | Must have ROLE_ADMIN (Spring adds "ROLE_" prefix automatically) |
| `.hasAuthority("ROLE_ADMIN")` | Exact authority string match |
| `.hasAnyRole("ADMIN", "MANAGER")` | Either role |
| `.denyAll()` | Nobody allowed |

**URL rules are evaluated in order — first match wins.**

---

## Method-Based Authorization with @PreAuthorize

More granular control: put authorization logic on individual service or controller methods.

Enable with:
```java
@EnableMethodSecurity  // already in SecurityConfig from Module 5 File 01
```

### Role Checks

```java
// Only ADMIN can create users
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/users")
public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest req) { ... }

// ADMIN or MANAGER
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@GetMapping("/api/reports")
public ReportResponse getReport() { ... }

// Must be authenticated (any role)
@PreAuthorize("isAuthenticated()")
@GetMapping("/api/profile")
public UserResponse getProfile() { ... }
```

### Parameter-Based Checks (SpEL)

Spring Security's method security supports **Spring Expression Language (SpEL)** — you can access method arguments:

```java
// User can only access their own profile (unless ADMIN)
@PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
@GetMapping("/api/users/{userId}")
public UserResponse getUser(@PathVariable Long userId) { ... }

// Check that the project belongs to the current user
@PreAuthorize("@projectSecurityService.isOwner(#projectId, authentication.principal.id)")
@DeleteMapping("/api/projects/{projectId}")
public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) { ... }
```

**Custom security service for complex rules:**
```java
// src/main/java/com/taskforge/security/ProjectSecurityService.java
@Service("projectSecurityService")  // bean name matters — used in @PreAuthorize
@RequiredArgsConstructor
public class ProjectSecurityService {

    private final ProjectRepository projectRepository;

    public boolean isOwner(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
            .map(p -> p.getOwner().getId().equals(userId))
            .orElse(false);
    }

    public boolean isMember(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
            .map(p -> p.isMember(userId))
            .orElse(false);
    }

    public boolean isOwnerOrMember(Long projectId, Long userId) {
        return isOwner(projectId, userId) || isMember(projectId, userId);
    }
}
```

Usage:
```java
// Only project owner or member can view tasks
@PreAuthorize("@projectSecurityService.isOwnerOrMember(#projectId, authentication.principal.id)")
@GetMapping("/api/projects/{projectId}/tasks")
public List<TaskResponse> getProjectTasks(@PathVariable Long projectId) { ... }
```

---

## @PostAuthorize

Check authorization *after* the method runs — useful when you need the method's return value:

```java
// User can only see their own task details (checked after loading from DB)
@PostAuthorize("returnObject.createdBy.id == authentication.principal.id or hasRole('ADMIN')")
@GetMapping("/api/tasks/{id}")
public TaskResponse getTask(@PathVariable Long id) {
    return taskService.findById(id);
}
```

> Use `@PostAuthorize` sparingly — it runs the method (DB query) even if access is ultimately denied.

---

## RBAC in TaskForge

TaskForge has 3 roles: `ADMIN`, `MANAGER`, `USER`.

| Action | ADMIN | MANAGER | USER |
|--------|-------|---------|------|
| Register/login | ✅ | ✅ | ✅ |
| View own profile | ✅ | ✅ | ✅ |
| Create project | ✅ | ✅ | ❌ |
| View project (member) | ✅ | ✅ | ✅ |
| Delete project | ✅ (owner) | ✅ (owner only) | ❌ |
| Create task (member) | ✅ | ✅ | ✅ |
| Delete any task | ✅ | ❌ | ❌ |
| Delete own task | ✅ | ✅ | ✅ |
| List all users | ✅ | ❌ | ❌ |
| Change user roles | ✅ | ❌ | ❌ |

**Implementation approach:**
1. Coarse-grained role checks via `@PreAuthorize("hasRole(...)")`
2. Fine-grained ownership/membership checks via `@ProjectSecurityService`
3. Business-level checks in service methods (is this user a project member?)

---

## Putting It Together: TaskController with Authorization

```java
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // Any authenticated user can get tasks (membership checked in service)
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(
            @RequestParam Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(taskService.findAll(projectId, currentUser));
    }

    // Any project member can create a task
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(taskService.create(request, currentUser));
    }

    // Only ADMIN or task owner can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @taskSecurityService.isCreator(#id, authentication.principal.id)")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

```java
// src/main/java/com/taskforge/security/TaskSecurityService.java
@Service("taskSecurityService")
@RequiredArgsConstructor
public class TaskSecurityService {
    private final TaskRepository taskRepository;

    public boolean isCreator(Long taskId, Long userId) {
        return taskRepository.findById(taskId)
            .map(t -> t.getCreatedBy().getId().equals(userId))
            .orElse(false);
    }
}
```

---

## SecurityContext — Accessing the Current User

The `SecurityContext` holds the current authentication for the active thread.

**In controllers (preferred):**
```java
@GetMapping("/profile")
public UserResponse getProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
    return new UserResponse(currentUser.getId(), currentUser.getName(), currentUser.getEmail(), ...);
}
```

**In services (when @AuthenticationPrincipal isn't available):**
```java
public UserPrincipal getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal p)) {
        throw new IllegalStateException("No authenticated user in context");
    }
    return p;
}
```

---

## @Secured — Simpler Alternative

```java
// Simpler than @PreAuthorize for role-only checks
@Secured("ROLE_ADMIN")
public void adminOnlyMethod() { ... }

@Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
public void adminOrManagerMethod() { ... }
```

`@Secured` doesn't support SpEL. Use `@PreAuthorize` when you need dynamic expressions.

---

## Try It Yourself

Test authorization rules with curl:

```bash
# Login as regular user
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","password":"password123"}' | jq -r '.data.accessToken')

# Login as admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r '.data.accessToken')

# Try to access admin-only endpoint as regular user
curl -s http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $USER_TOKEN"
# → 403 Forbidden

# Admin can access
curl -s http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
# → 200 + user list
```

---

## Capstone Connection

TaskForge's authorization is fully declarative:
- Roles defined in `SecurityConfig.authorizeHttpRequests()` for URL patterns
- Fine-grained ownership/membership via `ProjectSecurityService` and `TaskSecurityService`
- Current user injected via `@AuthenticationPrincipal UserPrincipal` in all controllers

The auth data flows: JWT token → `JwtAuthenticationFilter` → `SecurityContext` → `@AuthenticationPrincipal`.

**Next:** [03 — OAuth2](./03-oauth2.md) — third-party authentication (Google, GitHub).
