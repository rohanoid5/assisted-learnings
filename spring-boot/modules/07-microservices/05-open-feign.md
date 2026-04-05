# 05 — OpenFeign

## The Problem

When Project Service needs to call Task Service, you could use `RestTemplate` or `WebClient` manually:

```java
// Manual — verbose and repetitive
ResponseEntity<ApiResponse<List<TaskResponse>>> resp = restTemplate.exchange(
    "http://task-service/api/tasks?projectId={id}",
    HttpMethod.GET,
    new HttpEntity<>(headers),
    new ParameterizedTypeReference<ApiResponse<List<TaskResponse>>>() {},
    projectId
);
```

**OpenFeign** generates this code for you from a simple interface.

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

```java
@SpringBootApplication
@EnableFeignClients   // scan for @FeignClient interfaces
public class ProjectServiceApplication { ... }
```

---

## Feign Client Interface

```java
// In project-service — client to call task-service
@FeignClient(
    name = "task-service",          // Eureka service name (or URL for hardcoded)
    path = "/api/tasks",
    configuration = FeignConfig.class
)
public interface TaskServiceClient {

    @GetMapping
    ApiResponse<Page<TaskResponse>> getTasksByProject(
        @RequestParam Long projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    );

    @GetMapping("/{id}")
    ApiResponse<TaskResponse> getTask(@PathVariable Long id);

    @PostMapping
    ApiResponse<TaskResponse> createTask(@RequestBody CreateTaskRequest request,
                                         @RequestHeader("Authorization") String authHeader);

    @PatchMapping("/{id}/status")
    ApiResponse<TaskResponse> updateStatus(@PathVariable Long id,
                                            @RequestBody UpdateTaskStatusRequest request,
                                            @RequestHeader("Authorization") String authHeader);

    @DeleteMapping("/{id}")
    void deleteTask(@PathVariable Long id,
                   @RequestHeader("Authorization") String authHeader);
}
```

---

## Using the Feign Client

```java
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskServiceClient taskServiceClient;

    public ProjectDetailsResponse getProjectWithTasks(Long projectId, String authHeader) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // Call Task Service — Feign handles HTTP under the hood
        Page<TaskResponse> tasks = taskServiceClient
            .getTasksByProject(projectId, 0, 20)
            .data();

        return new ProjectDetailsResponse(
            project.getId(),
            project.getName(),
            tasks.getContent(),
            tasks.getTotalElements()
        );
    }
}
```

---

## Feign Configuration

```java
@Configuration
public class FeignConfig {

    // Log request/response for debugging
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;   // NONE, BASIC, HEADERS, FULL
    }

    // Custom error decoder
    @Bean
    ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new ResourceNotFoundException("Remote resource not found");
            }
            if (response.status() == 403) {
                return new AccessDeniedException("Remote: access denied");
            }
            return new RuntimeException("Feign error: " + response.status());
        };
    }

    // Timeout configuration
    @Bean
    Request.Options options() {
        return new Request.Options(
            Duration.ofSeconds(2),    // connect timeout
            Duration.ofSeconds(5),    // read timeout
            true                      // follow redirects
        );
    }
}
```

---

## Forwarding JWT Between Services

When a user calls Project Service (with JWT), Project Service must forward that JWT to Task Service:

### Option 1: Pass header explicitly (shown above)
```java
taskServiceClient.createTask(request, authHeader);  // pass "Bearer ..." explicitly
```

### Option 2: Feign RequestInterceptor (automatic)

```java
@Bean
RequestInterceptor jwtForwardingInterceptor(HttpServletRequest httpRequest) {
    return requestTemplate -> {
        // Grab JWT from current HTTP request and forward it
        String auth = httpRequest.getHeader("Authorization");
        if (auth != null) {
            requestTemplate.header("Authorization", auth);
        }
    };
}
```

With this interceptor, all Feign requests automatically include the current user's JWT.

---

## Testing Feign Clients

Use `WireMock` to stub the downstream service:

```xml
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.3.1</version>
    <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
@ActiveProfiles("test")
class TaskServiceClientTest {

    @Autowired TaskServiceClient taskServiceClient;

    static WireMockServer wireMockServer = new WireMockServer(8083);

    @BeforeAll
    static void start() { wireMockServer.start(); }

    @AfterAll
    static void stop() { wireMockServer.stop(); }

    @Test
    void getTask_shouldReturnResponse() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/tasks/1"))
                .willReturn(WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"success":true,"data":{"id":1,"title":"Test task"}}
                    """))
        );

        ApiResponse<TaskResponse> response = taskServiceClient.getTask(1L);
        assertThat(response.data().title()).isEqualTo("Test task");
    }
}
```

---

## Next

[06 — Resilience4j Circuit Breaker](./06-circuit-breaker.md) — handle service failures gracefully.
