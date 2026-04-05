# Module 6 Exercises — Testing

Build a comprehensive test suite for TaskForge covering unit tests, slice tests, and integration tests.

---

## Exercise 1 — Unit Test TaskService

**Goal:** Write pure Mockito unit tests for `TaskService` without Spring context.

**Setup:**
```java
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;

    @InjectMocks TaskService taskService;

    // Shared test data — built in @BeforeEach
    User alice;
    Project project;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).name("Alice").email("alice@test.com")
            .role(Role.USER).build();
        project = Project.builder().id(1L).name("TaskForge").owner(alice).build();
    }
}
```

**Write these test methods:**

1. `createTask_success_shouldReturnTaskResponse` — happy path
2. `createTask_projectNotFound_shouldThrowResourceNotFoundException`
3. `createTask_assigneeNotFound_shouldThrowResourceNotFoundException`
4. `updateTaskStatus_byCreator_shouldSucceed`
5. `updateTaskStatus_byNonCreator_shouldThrowAccessDeniedException`
6. `deleteTask_byOwner_shouldCallRepositoryDelete`
7. `getTaskById_notFound_shouldThrow`

**Hint for status update test:**
```java
@Test
void updateTaskStatus_byNonCreator_shouldThrowAccessDeniedException() {
    Task task = Task.builder()
        .id(10L)
        .creator(User.builder().id(99L).build())  // different user
        .status(TaskStatus.TODO)
        .build();
    given(taskRepository.findById(10L)).willReturn(Optional.of(task));

    assertThatThrownBy(() -> taskService.updateTaskStatus(10L, TaskStatus.IN_PROGRESS, alice.getId()))
        .isInstanceOf(AccessDeniedException.class);

    verify(taskRepository, never()).save(any());
}
```

---

## Exercise 2 — @DataJpaTest for TaskRepository

**Goal:** Test all custom repository query methods against a real H2 database.

**Setup:**
```java
@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TaskRepository taskRepository;

    User owner;
    Project project;

    @BeforeEach
    void setUp() {
        owner = em.persistAndFlush(User.builder()
            .name("Owner").email("owner@repo.com")
            .passwordHash("hash").role(Role.USER).build());
        project = em.persistAndFlush(Project.builder()
            .name("Repo Test Project").owner(owner).build());
    }
}
```

**Write these test methods:**

1. `findByProjectId_shouldReturnAllProjectTasks`
2. `findByProjectIdAndStatus_shouldOnlyReturnMatchingStatus`
3. `findByProjectId_withPageable_secondPageShouldHaveCorrectSize`
4. `findByAssignee_shouldOnlyReturnAssignedTasks`

**For (3):** Create 15 tasks, request page 1 (0-indexed) with size 10 — expect 5 results.

---

## Exercise 3 — MockMvc Test for TaskController

**Goal:** Test the `TaskController` HTTP layer with mocked services.

```java
@WebMvcTest(TaskController.class)
@ActiveProfiles("test")
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TaskService taskService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;
}
```

**Write these test methods:**

1. `getTaskById_withValidToken_shouldReturn200AndTaskData`
    - Stub `taskService.getTaskById()` to return a `TaskResponse`
    - Perform `GET /api/tasks/1` with Authorization header
    - Assert 200, `$.data.title` exists

2. `getTaskById_withoutToken_shouldReturn401`

3. `createTask_withValidData_shouldReturn201`
    - Serialize a `CreateTaskRequest`, post it
    - Assert 201 and `$.data.id` is a number

4. `createTask_withEmptyTitle_shouldReturn400AndValidationError`
    - Post a request with blank title
    - Assert 400, `$.success` is false

5. `updateTaskStatus_toDone_shouldReturn200`

**Handling security in @WebMvcTest:** Use `@WithMockUser` or disable security:
```java
@TestConfiguration
static class DisabledSecurity {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
```
Annotate your test class: `@Import(TaskControllerTest.DisabledSecurity.class)`

---

## Exercise 4 — Integration Test with @SpringBootTest

**Goal:** Write an end-to-end test using `TestRestTemplate` against a real embedded server.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskForgeIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    private String token;
    private Long projectId;
}
```

**Write this scenario as sequential @Test methods (use @TestMethodOrder(MethodOrderer.OrderAnnotation.class)):**

```java
@Test @Order(1)
void registerUser_shouldSucceed() { ... }

@Test @Order(2)
void loginUser_shouldReturnJwt() { ... }   // store token in field

@Test @Order(3)
void createProject_shouldReturnCreated() { ... }  // store projectId

@Test @Order(4)
void createTask_inProject_shouldSucceed() { ... }

@Test @Order(5)
void listTasks_forProject_shouldReturnTask() { ... }
```

**Authentication helper:**
```java
private HttpEntity<T> withAuth(T body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
}
```

---

## Exercise 5 — Test Edge Cases and Error Paths

**Goal:** Write tests that verify error handling in `GlobalExceptionHandler`.

Using MockMvc:

```java
@Test
void taskNotFound_shouldReturn404() throws Exception {
    given(taskService.getTaskById(eq(999L), any()))
        .willThrow(new ResourceNotFoundException("Task", "id", 999L));

    mockMvc.perform(get("/api/tasks/999")
        .header("Authorization", "Bearer token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("999")));
}

@Test
void unauthorizedAccess_shouldReturn403() throws Exception {
    given(taskService.deleteTask(eq(1L), any()))
        .willThrow(new AccessDeniedException("Not authorized"));

    mockMvc.perform(delete("/api/tasks/1")
        .header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
}
```

**Also test:**
- Malformed JSON body → 400
- Wrong content type → 415
- Missing required path variable → 400

---

## Module 6 Capstone Checkpoint

```
Unit tests (TaskService):
[ ] All 7 TaskService test cases pass
[ ] No Spring context loaded in service tests (fast execution)
[ ] ArgumentCaptor used to verify entity fields

Repository tests (TaskRepository):
[ ] findByProjectId returns correct tasks
[ ] Status filter works correctly
[ ] Pagination returns correct page/total
[ ] Each test rolls back (no state leaking between tests)

MockMvc tests (TaskController):
[ ] All CRUD operations tested
[ ] Validation errors produce 400 responses
[ ] Missing auth produces 401

Integration test:
[ ] Full register → login → create project → create task → list tasks flow passes
```

---

## Up Next

[Module 7 — Microservices](../../07-microservices/README.md) — breaking TaskForge into services linked by HTTP clients and event streams.
