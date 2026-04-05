# 01 — @SpringBootTest

## The Testing Pyramid

```
         ╱▔▔▔▔▔▔▔▔╲
        ╱  E2E Tests  ╲      Few, slow, expensive — full stack
       ╱────────────────╲
      ╱ Integration Tests ╲   Some — test multiple layers together
     ╱────────────────────╲
    ╱     Unit Tests        ╲  Many, fast, cheap — test one class in isolation
   ╱────────────────────────╲
```

Spring Boot gives you dedicated tooling for every layer.

---

## Test Scopes in Spring Boot

| Annotation | What loads | Speed | Use when |
|-----------|-----------|-------|---------|
| `@SpringBootTest` | Full application context | Slow | Testing across multiple layers |
| `@WebMvcTest` | Web layer only | Fast | Testing controllers in isolation |
| `@DataJpaTest` | JPA layer + H2 | Medium | Testing repositories |
| No annotation (plain JUnit) | Nothing | Fastest | Pure unit tests |

**Analogy (Node.js):** Like the difference between a full Jest integration test with a real server, a supertest test with mocked services, and a pure unit test of a function.

---

## @SpringBootTest Basics

```java
@SpringBootTest  // loads entire Spring application context
class TaskServiceIntegrationTest {

    @Autowired
    private TaskService taskService;   // real fully-wired service

    @Test
    void shouldCreateTask() {
        // Real service, real repo, real DB (H2 in test profile)
        CreateTaskRequest request = new CreateTaskRequest("Fix login bug", ...);
        TaskResponse result = taskService.createTask(request, 1L);
        assertThat(result.title()).isEqualTo("Fix login bug");
    }
}
```

By default, `@SpringBootTest` loads the full context and uses an embedded H2 database if you have it on the test classpath.

---

## Test Configuration — application-test.yml

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop   # recreate schema for each test run
    show-sql: false           # don't flood test output

app:
  jwt:
    secret: "test-secret-key-exactly-64-characters-long-for-hmac-sha-algorithm!!"
    expiration-ms: 3600000    # 1 hour for tests
```

Activate with `@ActiveProfiles("test")`:
```java
@SpringBootTest
@ActiveProfiles("test")
class MyIntegrationTest { ... }
```

---

## WebEnvironment Modes

```java
// Mode 1: MOCK (default) — no real HTTP server, use MockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class MockTest { ... }

// Mode 2: RANDOM_PORT — real HTTP server on random port
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RealHttpTest {

    @Autowired
    TestRestTemplate restTemplate;   // pre-configured to talk to the test server

    @LocalServerPort
    int port;

    @Test
    void shouldReturnProjects() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/projects", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

Use `RANDOM_PORT` when you need to test the full HTTP stack (including filters, servlet container behavior). Use `MOCK` when MockMvc is sufficient.

---

## TestRestTemplate

`TestRestTemplate` is a test-friendly wrapper around `RestTemplate`. Available only with `RANDOM_PORT` or `DEFINED_PORT`.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void registerAndLogin() {
        // Register
        var registerBody = new RegisterRequest("Alice", "alice@test.com", "Password1!");
        ResponseEntity<ApiResponse> regResp = restTemplate.postForEntity(
            "/api/auth/register", registerBody, ApiResponse.class);

        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResp.getBody().success()).isTrue();

        // Login
        var loginBody = new LoginRequest("alice@test.com", "Password1!");
        ResponseEntity<ApiResponse> loginResp = restTemplate.postForEntity(
            "/api/auth/login", loginBody, ApiResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_withWrongPassword_shouldReturn401() {
        var loginBody = new LoginRequest("alice@test.com", "wrong");
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
            "/api/auth/login", loginBody, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

---

## Sending Authenticated Requests

```java
private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
}

@Test
void createProject_withValidToken_shouldSucceed() {
    String token = loginAndGetToken();   // helper method

    CreateProjectRequest body = new CreateProjectRequest("TaskForge Alpha", "...", null);
    HttpEntity<CreateProjectRequest> request = new HttpEntity<>(body, bearerHeaders(token));

    ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
        "/api/projects", request, ApiResponse.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
}
```

---

## Test Data Management

### @Sql — run SQL scripts before/after
```java
@Test
@Sql("/sql/seed-projects.sql")           // runs before test
@Sql(scripts = "/sql/cleanup.sql",
     executionPhase = ExecutionPhase.AFTER_TEST_METHOD)  // runs after
void shouldListProjects() {
    // DB has seed data
}
```

### @Transactional on tests — auto-rollback
```java
@SpringBootTest
@Transactional   // each test runs in a transaction that is rolled back afterward
class TransactionalTest {

    @Autowired TaskRepository taskRepository;

    @Test
    void createdTaskIsRolledBack() {
        taskRepository.save(Task.builder().title("temp").build());
        assertThat(taskRepository.count()).isEqualTo(1);
        // After test: rolled back — no cleanup needed
    }
}
```

> **Gotcha:** `@Transactional` on integration tests with `RANDOM_PORT` doesn't work — the HTTP request runs in a different thread/transaction. Use `@Sql` cleanup scripts instead.

---

## Capstone Connection

**Full integration test scenario:**
```
1. Register user (POST /api/auth/register)
2. Login (POST /api/auth/login) → get JWT
3. Create project (POST /api/projects)
4. Create task in project (POST /api/tasks)
5. Assign task (PATCH /api/tasks/{id}/status)
6. Add comment (POST /api/tasks/{id}/comments)
7. Fetch project with tasks (GET /api/projects/{id})
```

This end-to-end test gives you the highest confidence the whole system works together.

---

## Next

[02 — @MockBean and Mockito](./02-mockbean.md) — unit testing services in isolation.
