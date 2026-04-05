# Module 4 — Spring Data Exercises

## Overview

These exercises replace the in-memory stub services from Module 3 with real PostgreSQL persistence using JPA and Spring Data.

---

## Prerequisites

Start PostgreSQL:
```bash
docker run -d \
  --name taskforge-db \
  -e POSTGRES_DB=taskforge \
  -e POSTGRES_USER=taskforge \
  -e POSTGRES_PASSWORD=taskforge_dev \
  -p 5432:5432 \
  postgres:15-alpine
```

Verify connection:
```bash
docker exec -it taskforge-db psql -U taskforge -d taskforge -c 'SELECT version();'
```

---

## Exercise 1 — Create Domain Entities

Create all 4 entity classes and 3 enum classes:

**Step 1 — Enums:**
```java
// src/main/java/com/taskforge/domain/Role.java
// src/main/java/com/taskforge/domain/TaskStatus.java
// src/main/java/com/taskforge/domain/Priority.java
```
(See Module 3 Exercise 1 for starter code, or the full definitions in `03-relationships.md`)

**Step 2 — Entities:**

Create these 4 files using the full code from `03-relationships.md`:
- `src/main/java/com/taskforge/domain/User.java`
- `src/main/java/com/taskforge/domain/Project.java`
- `src/main/java/com/taskforge/domain/Task.java`
- `src/main/java/com/taskforge/domain/Comment.java`

**Step 3 — Configure application-dev.yml:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskforge
    username: taskforge
    password: taskforge_dev
    hikari:
      maximum-pool-size: 5
  jpa:
    open-in-view: false
    show-sql: true
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
```

**Step 4 — Run and verify:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Look for Hibernate DDL in logs:
# create table users (...)
# create table projects (...)
# create table tasks (...)
# create table comments (...)
# create table project_members (...)
```

Connect and inspect:
```bash
docker exec -it taskforge-db psql -U taskforge -d taskforge
\dt        -- list tables
\d tasks   -- describe tasks table (verify FKs, columns)
```

---

## Exercise 2 — Create Repositories

Create all 4 repositories using the code in `05-spring-data-jpa.md`:

```java
// src/main/java/com/taskforge/repository/UserRepository.java
// src/main/java/com/taskforge/repository/ProjectRepository.java
// src/main/java/com/taskforge/repository/TaskRepository.java
// src/main/java/com/taskforge/repository/CommentRepository.java
```

**Test basic repository operations with a DataInitializer:**

```java
// src/main/java/com/taskforge/config/DataInitializer.java
@Component
@Profile("dev")  // only run in dev profile
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @PostConstruct
    @Transactional
    public void init() {
        if (userRepository.count() > 0) return; // skip if already seeded

        // Create users
        User alice = User.builder()
            .name("Alice")
            .email("alice@example.com")
            .password("$2a$10$hashed_password_here") // real hash in Module 5
            .role(Role.ADMIN)
            .build();
        userRepository.save(alice);

        User bob = User.builder()
            .name("Bob")
            .email("bob@example.com")
            .password("$2a$10$hashed_password_here")
            .role(Role.USER)
            .build();
        userRepository.save(bob);

        // Create project
        Project project = Project.builder()
            .name("TaskForge Development")
            .description("Building the TaskForge app while learning Spring Boot")
            .owner(alice)
            .build();
        project.addMember(bob);
        projectRepository.save(project);

        // Create tasks
        Task t1 = Task.builder()
            .title("Set up Spring Boot project")
            .status(TaskStatus.DONE)
            .priority(Priority.HIGH)
            .project(project)
            .createdBy(alice)
            .assignee(alice)
            .build();

        Task t2 = Task.builder()
            .title("Create entity models")
            .status(TaskStatus.IN_PROGRESS)
            .priority(Priority.HIGH)
            .project(project)
            .createdBy(alice)
            .assignee(bob)
            .build();

        Task t3 = Task.builder()
            .title("Implement Spring Security")
            .status(TaskStatus.TODO)
            .priority(Priority.MEDIUM)
            .project(project)
            .createdBy(alice)
            .build();

        taskRepository.saveAll(List.of(t1, t2, t3));
        log.info("Seeded {} users, {} projects, {} tasks", 
            userRepository.count(), projectRepository.count(), taskRepository.count());
    }
}
```

Start the app and verify data with:
```bash
curl -s http://localhost:8080/actuator/health | jq
# DB should report UP

docker exec -it taskforge-db psql -U taskforge -d taskforge -c 'SELECT * FROM users;'
docker exec -it taskforge-db psql -U taskforge -d taskforge -c 'SELECT * FROM tasks;'
```

---

## Exercise 3 — Wire Services to Repositories

Replace the stub `TaskService` from Module 3 with real JPA queries:

```java
// src/main/java/com/taskforge/service/TaskService.java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<TaskResponse> findAll(Long projectId) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public TaskResponse findById(Long id) {
        return taskRepository.findByIdWithDetails(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Project project = projectRepository.findById(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        Task task = Task.builder()
            .title(request.title())
            .description(request.description())
            .status(TaskStatus.TODO)
            .priority(request.priority() != null ? request.priority() : Priority.MEDIUM)
            .project(project)
            // TODO: set createdBy from authenticated user in Module 5
            .build();

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task", id);
        }
        taskRepository.deleteById(id);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getPriority(),
            task.getProject().getId(),
            task.getProject().getName(),
            task.getAssignee() != null ? toUserResponse(task.getAssignee()) : null,
            task.getCreatedBy() != null ? toUserResponse(task.getCreatedBy()) : null,
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
```

Test end-to-end:
```bash
# After starting with dev profile + data seeded:
curl -s "http://localhost:8080/api/tasks?projectId=1" | jq
# Should return real tasks from PostgreSQL

curl -s http://localhost:8080/api/tasks/1 | jq
# Should return task with assignee + createdBy fields populated
```

---

## Exercise 4 — @DataJpaTest: Repository Unit Tests

```java
// src/test/java/com/taskforge/repository/TaskRepositoryTest.java
@DataJpaTest  // loads only JPA layer, H2 in-memory
class TaskRepositoryTest {

    @Autowired TaskRepository taskRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired UserRepository userRepository;

    private User savedUser;
    private Project savedProject;

    @BeforeEach
    void setup() {
        savedUser = userRepository.save(User.builder()
            .name("Test User").email("test@test.com")
            .password("password").role(Role.USER).build());

        savedProject = projectRepository.save(Project.builder()
            .name("Test Project").owner(savedUser).build());
    }

    @Test
    void findByProjectId_returnsTasksForProject() {
        taskRepository.saveAll(List.of(
            Task.builder().title("T1").status(TaskStatus.TODO).priority(Priority.MEDIUM)
                .project(savedProject).createdBy(savedUser).build(),
            Task.builder().title("T2").status(TaskStatus.DONE).priority(Priority.HIGH)
                .project(savedProject).createdBy(savedUser).build()
        ));

        List<Task> found = taskRepository.findByProjectId(savedProject.getId());
        assertEquals(2, found.size());
    }

    @Test
    void findByProjectIdAndStatus_filtersCorrectly() {
        taskRepository.saveAll(List.of(
            Task.builder().title("T1").status(TaskStatus.TODO).priority(Priority.MEDIUM)
                .project(savedProject).createdBy(savedUser).build(),
            Task.builder().title("T2").status(TaskStatus.DONE).priority(Priority.LOW)
                .project(savedProject).createdBy(savedUser).build()
        ));

        List<Task> todoTasks = taskRepository.findByProjectIdAndStatus(savedProject.getId(), TaskStatus.TODO);
        assertEquals(1, todoTasks.size());
        assertEquals("T1", todoTasks.get(0).getTitle());
    }

    @Test
    void pagination_worksCorrectly() {
        for (int i = 0; i < 15; i++) {
            taskRepository.save(Task.builder()
                .title("Task " + i).status(TaskStatus.TODO).priority(Priority.LOW)
                .project(savedProject).createdBy(savedUser).build());
        }

        Page<Task> page = taskRepository.findByProjectId(savedProject.getId(),
            PageRequest.of(0, 10, Sort.by("createdAt").ascending()));

        assertEquals(10, page.getContent().size());
        assertEquals(15, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }
}
```

Run:
```bash
mvn test -Dtest=TaskRepositoryTest
```

---

## Exercise 5 — Observe N+1 and Fix It

**Reproduce N+1:**

Enable SQL logging and call an endpoint that accesses lazy associations in a loop. Check the console for repeated SELECT queries.

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

**Fix with @EntityGraph:**

Add this to TaskRepository:
```java
@EntityGraph(attributePaths = {"assignee", "createdBy", "project"})
@Query("SELECT t FROM Task t WHERE t.project.id = :projectId")
List<Task> findByProjectIdWithDetails(@Param("projectId") Long projectId);
```

Update your service to use `findByProjectIdWithDetails()` instead. Observe how the log goes from N+1 queries to a single JOIN query.

---

## Module 4 Capstone Checkpoint

- [ ] All 4 entities created with correct annotations
- [ ] All 3 enums created
- [ ] PostgreSQL running in Docker
- [ ] Hibernate auto-creates 5 tables on startup
- [ ] All 4 repositories created with custom query methods
- [ ] Data seeder populates test data on dev startup
- [ ] `GET /api/tasks?projectId=1` returns real data from DB
- [ ] `GET /api/tasks/1` returns task with assignee/createdBy populated
- [ ] @DataJpaTest passes for TaskRepository
- [ ] N+1 identified and fixed with @EntityGraph

**What you've built:** The full persistence layer. TaskForge now stores real data in PostgreSQL.

**Next:** [Module 5 — Spring Security](../../05-spring-security/README.md) — authentication, JWT, and RBAC.
