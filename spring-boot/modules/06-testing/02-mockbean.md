# 02 — @MockBean and Mockito

## Why Mock?

Unit tests should test **one class in isolation**. If `TaskService` is tested alongside a real `TaskRepository`, your test breaks when the DB is unavailable — that's not a `TaskService` bug.

**Mocking:** Replace real collaborators with controlled fakes.

```
Real test:     TaskService → TaskRepository → PostgreSQL → disk I/O
Isolated test: TaskService → MockRepository (in-memory, deterministic)
```

---

## Mockito vs @MockBean

| | `@Mock` (pure Mockito) | `@MockBean` (Spring) |
|-|-----------------------|--------------------|
| Loads Spring context | ❌ No | ✅ Yes |
| Speed | Very fast | Slower |
| Injects into Spring beans | ❌ Manual | ✅ Automatic |
| Use with | `@ExtendWith(MockitoExtension.class)` | `@WebMvcTest` / `@SpringBootTest` |

**Rule of thumb:**
- Service layer tests → pure Mockito (`@Mock`)
- Controller tests → `@MockBean` with `@WebMvcTest`

---

## Pure Unit Test with Mockito

```java
@ExtendWith(MockitoExtension.class)     // enables @Mock/@InjectMocks
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks                         // injects all @Mocks into this
    TaskService taskService;

    @Test
    void createTask_shouldSaveAndReturnResponse() {
        // Arrange
        Long projectId = 1L;
        Long userId = 42L;

        Project mockProject = Project.builder().id(projectId).name("TaskForge").build();
        User mockUser = User.builder().id(userId).name("Alice").email("alice@test.com").build();

        Task savedTask = Task.builder()
            .id(100L)
            .title("Fix login bug")
            .status(TaskStatus.TODO)
            .priority(Priority.HIGH)
            .project(mockProject)
            .creator(mockUser)
            .build();

        given(projectRepository.findById(projectId)).willReturn(Optional.of(mockProject));
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
        given(taskRepository.save(any(Task.class))).willReturn(savedTask);

        CreateTaskRequest request = new CreateTaskRequest(
            "Fix login bug", null, Priority.HIGH, null, projectId);

        // Act
        TaskResponse response = taskService.createTask(request, userId);

        // Assert
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("Fix login bug");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);

        // Verify interactions
        verify(taskRepository, times(1)).save(any(Task.class));
        verify(projectRepository).findById(projectId);
    }

    @Test
    void createTask_withNonExistentProject_shouldThrow() {
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        CreateTaskRequest request = new CreateTaskRequest("...", null, Priority.LOW, null, 999L);

        assertThatThrownBy(() -> taskService.createTask(request, 1L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Project");
    }
}
```

---

## Mockito Essentials

### Stubbing with given().willReturn()
```java
// Return a value
given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(alice));

// Return different values on successive calls
given(service.nextId()).willReturn(1L, 2L, 3L);

// Throw an exception
given(taskRepository.save(any())).willThrow(new DataIntegrityViolationException("..."));

// Return the argument passed in
given(taskRepository.save(any(Task.class))).willAnswer(inv -> inv.getArgument(0));
```

### Argument matchers
```java
any()                        // any object
any(Task.class)              // any Task
anyLong()                    // any long primitive
eq(42L)                      // exact value
argThat(t -> t.getId() > 0)  // custom predicate
```

### Verification
```java
verify(taskRepository).save(any(Task.class));                    // called once
verify(taskRepository, times(2)).save(any());                    // called exactly twice
verify(taskRepository, never()).delete(any());                   // never called
verify(taskRepository, atLeastOnce()).findById(anyLong());
verifyNoMoreInteractions(taskRepository);                        // strict: no unexpected calls
```

---

## Testing Exception Cases

```java
@Test
void updateTask_whenNotFound_shouldThrow() {
    given(taskRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> taskService.updateTask(999L, updateRequest))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Task not found with id: 999");
}

@Test
void deleteTask_whenUnauthorized_shouldThrow() {
    Long taskId = 1L;
    Long otherUserId = 99L;

    Task task = Task.builder()
        .id(taskId)
        .creator(User.builder().id(7L).build())   // created by user 7
        .build();
    given(taskRepository.findById(taskId)).willReturn(Optional.of(task));

    assertThatThrownBy(() -> taskService.deleteTask(taskId, otherUserId))
        .isInstanceOf(AccessDeniedException.class);
}
```

---

## @MockBean in Spring Context

Use `@MockBean` to replace a real Spring bean with a mock within the application context. Essential with `@WebMvcTest`:

```java
@WebMvcTest(TaskController.class)   // loads only the web layer
class TaskControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean TaskService taskService;         // replaces real TaskService in context
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void getTask_shouldReturnTaskResponse() throws Exception {
        TaskResponse mockTask = new TaskResponse(1L, "Fix bug", TaskStatus.TODO, Priority.HIGH, ...);
        given(taskService.getTaskById(1L, 42L)).willReturn(mockTask);

        mockMvc.perform(get("/api/tasks/1")
            .header("Authorization", "Bearer fake-token"))    // filter mocked out
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Fix bug"));
    }
}
```

---

## @SpyBean — Partial Mocking

A spy wraps a real bean but lets you override specific methods:

```java
@SpringBootTest
class EmailNotificationTest {

    @SpyBean
    EmailService emailService;    // real bean, but can override methods

    @Test
    void createTask_shouldSendNotification() {
        doNothing().when(emailService).sendTaskAssignmentEmail(any());  // skip real email

        taskService.assignTask(taskId, assigneeId);

        verify(emailService).sendTaskAssignmentEmail(argThat(
            e -> e.to().equals("assignee@test.com")
        ));
    }
}
```

---

## ArgumentCaptor — Inspect What Was Passed

```java
@Test
void createTask_shouldPersistCorrectEntity() {
    ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);

    given(taskRepository.save(taskCaptor.capture())).willAnswer(inv -> {
        Task t = inv.getArgument(0);
        t.setId(1L);
        return t;
    });

    taskService.createTask(request, userId);

    Task saved = taskCaptor.getValue();
    assertThat(saved.getTitle()).isEqualTo(request.title());
    assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);       // default status
    assertThat(saved.getCreator().getId()).isEqualTo(userId);
}
```

---

## Capstone Connection

**Full service unit test strategy:**

| Service | Key test cases |
|---------|---------------|
| `AuthService.register` | Success, duplicate email throws, password is hashed |
| `AuthService.login` | Success, wrong password throws |
| `ProjectService.create` | Success, owner assigned correctly |
| `ProjectService.addMember` | Success, user not found throws |
| `TaskService.create` | Success, project not found throws, user not found throws |
| `TaskService.updateStatus` | Success, unauthorized throws, task not found throws |
| `CommentService.addComment` | Success, task not found throws, non-member throws |

---

## Next

[03 — MockMvc](./03-mock-mvc.md) — testing the HTTP layer without a real server.
