# 03 — MockMvc

## What Is MockMvc?

MockMvc tests the web layer (controllers, filters, validation, serialization) **without starting a real HTTP server**. It dispatches requests through the DispatcherServlet in a simulated environment.

**Analogy (Node.js):** Like `supertest` — you write HTTP-style assertions, but no actual TCP socket is involved.

```javascript
// Node.js supertest
const response = await request(app).get('/api/tasks/1');
expect(response.status).toBe(200);

// Spring MockMvc — same idea
mockMvc.perform(get("/api/tasks/1"))
       .andExpect(status().isOk());
```

---

## Setup with @WebMvcTest

`@WebMvcTest` loads only the web layer — no service beans, no repositories:

```java
@WebMvcTest(TaskController.class)
@ActiveProfiles("test")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;   // for serializing request bodies

    // Mock all real dependencies of TaskController
    @MockBean private TaskService taskService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // TaskController is real; everything else is a mock
}
```

---

## Basic Request/Response Pattern

```java
@Test
void getTask_shouldReturnOk() throws Exception {
    // Arrange
    TaskResponse mockTask = new TaskResponse(
        1L, "Fix login bug", TaskStatus.TODO, Priority.HIGH,
        "Alice", LocalDateTime.now(), null, List.of()
    );
    given(taskService.getTaskById(eq(1L), any())).willReturn(mockTask);

    // Act + Assert
    mockMvc.perform(
        get("/api/tasks/1")
            .header("Authorization", "Bearer test-token")
            .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.data.id").value(1))
    .andExpect(jsonPath("$.data.title").value("Fix login bug"))
    .andExpect(jsonPath("$.data.status").value("TODO"));
}
```

---

## Common MockMvc Operations

### GET requests
```java
mockMvc.perform(
    get("/api/tasks")
        .param("projectId", "1")
        .param("status", "TODO")
        .param("page", "0")
        .param("size", "10")
        .header("Authorization", "Bearer token")
)
.andExpect(status().isOk())
.andExpect(jsonPath("$.data.content").isArray())
.andExpect(jsonPath("$.data.totalElements").value(5));
```

### POST with body
```java
CreateTaskRequest createRequest = new CreateTaskRequest(
    "New task", "Description", Priority.HIGH, null, 1L);

mockMvc.perform(
    post("/api/tasks")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest))
)
.andExpect(status().isCreated())
.andExpect(jsonPath("$.data.id").isNumber())
.andExpect(jsonPath("$.data.title").value("New task"));
```

### PUT (update)
```java
UpdateTaskRequest updateRequest = new UpdateTaskRequest("Updated title", null, Priority.LOW, null);

mockMvc.perform(
    put("/api/tasks/1")
        .header("Authorization", "Bearer token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(updateRequest))
)
.andExpect(status().isOk());
```

### DELETE
```java
mockMvc.perform(
    delete("/api/tasks/1")
        .header("Authorization", "Bearer token")
)
.andExpect(status().isNoContent());
```

---

## jsonPath Cheatsheet

```java
// Scalar values
.andExpect(jsonPath("$.success").value(true))
.andExpect(jsonPath("$.data.id").value(42))
.andExpect(jsonPath("$.data.name").value("Alice"))

// Type assertions
.andExpect(jsonPath("$.data.id").isNumber())
.andExpect(jsonPath("$.data.title").isString())
.andExpect(jsonPath("$.data.tags").isArray())

// Existence
.andExpect(jsonPath("$.data.token").exists())
.andExpect(jsonPath("$.data.password").doesNotExist())

// Array length
.andExpect(jsonPath("$.data.tasks").isArray())
.andExpect(jsonPath("$.data.tasks", hasSize(3)))

// Nested
.andExpect(jsonPath("$.data.project.name").value("TaskForge"))
.andExpect(jsonPath("$.data.tasks[0].title").value("First task"))
.andExpect(jsonPath("$.data.tasks[*].status", everyItem(is("TODO"))))
```

Import for Hamcrest matchers:
```java
import static org.hamcrest.Matchers.*;
```

---

## Testing Validation Errors

```java
@Test
void createTask_withEmptyTitle_shouldReturn400() throws Exception {
    CreateTaskRequest invalidRequest = new CreateTaskRequest(
        "",       // @NotBlank violation
        null,
        Priority.HIGH,
        null,
        1L
    );

    mockMvc.perform(
        post("/api/tasks")
            .header("Authorization", "Bearer token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalidRequest))
    )
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.success").value(false))
    .andExpect(jsonPath("$.message").exists());
}
```

---

## Testing Security with MockMvc

### Security is fully wired in @WebMvcTest

By default, Spring Security is active even in `@WebMvcTest` tests. You need to either:
1. Mock the JWT filter so it sets the `SecurityContext`
2. Use `@WithMockUser` for simple role-based tests

```java
@Test
@WithMockUser(username = "alice@test.com", roles = {"USER"})
void getTask_withMockUser_shouldReturnOk() throws Exception {
    given(taskService.getTaskById(1L, any())).willReturn(mockTask);

    mockMvc.perform(get("/api/tasks/1"))
        .andExpect(status().isOk());
}
```

```java
@Test
void getTask_withoutAuth_shouldReturn401() throws Exception {
    mockMvc.perform(get("/api/tasks/1"))
        .andExpect(status().isUnauthorized());
}
```

### Custom annotation for authenticated requests
```java
// Create a reusable annotation
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(username = "alice@test.com", roles = {"USER"})
public @interface WithMockAlice {}

// Usage
@Test
@WithMockAlice
void shouldAllowRegularUser() { ... }
```

### Fully wiring JWT in tests

For tests where the controller reads `@AuthenticationPrincipal`, you need a real `UserPrincipal` in the context. This requires a custom `WithSecurityContextFactory`:

```java
// Option 1: simpler — override SecurityConfig for tests
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

// Use in test:
@WebMvcTest(TaskController.class)
@Import(TestSecurityConfig.class)
class TaskControllerTest { ... }
```

---

## andDo(print()) — Debug Helper

```java
mockMvc.perform(get("/api/tasks/1").header("Authorization", "Bearer token"))
    .andDo(print())   // prints full request + response to console
    .andExpect(status().isOk());
```

Output:
```
MockHttpServletRequest:
  HTTP Method = GET
  Request URI = /api/tasks/1
  ...

MockHttpServletResponse:
  Status = 200
  Content-Type = application/json
  Body = {"success":true,"data":{"id":1,"title":"Fix login bug",...}}
```

---

## Extracting the Response

```java
MvcResult result = mockMvc.perform(post("/api/tasks").content(...))
    .andExpect(status().isCreated())
    .andReturn();

String responseBody = result.getResponse().getContentAsString();
ApiResponse<TaskResponse> response = objectMapper.readValue(
    responseBody,
    new TypeReference<ApiResponse<TaskResponse>>() {}
);

assertThat(response.data().id()).isPositive();
```

---

## Capstone Connection

**Controller tests to write:**

| Controller | Tests |
|-----------|-------|
| `AuthController` | register success, register duplicate, login success, login wrong password |
| `ProjectController` | list, get by id (member), get by id (non-member → 403), create, update (owner), delete (admin only) |
| `TaskController` | list with filters, get by id, create, update, update status, delete |
| `CommentController` | list for task, add comment, delete own comment, delete other's comment → 403 |

---

## Next

[04 — @DataJpaTest](./04-jpa-test.md) — testing the persistence layer without the full application context.
