# 04 — @DataJpaTest

## What Is @DataJpaTest?

`@DataJpaTest` loads only the JPA layer:
- Configures an **in-memory H2 database**
- Scans `@Entity` classes and Spring Data repositories
- Does **not** load `@Service`, `@Controller`, or `@Component` beans
- Wraps each test in a transaction that is **rolled back** after the test

**Perfect for:** Testing your repository methods and custom `@Query` expressions.

---

## Basic Setup

```xml
<!-- Add H2 to test scope in pom.xml -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired
    private TestEntityManager em;     // helper for test setup

    @Autowired
    private TaskRepository taskRepository;

    // Test methods here
}
```

---

## TestEntityManager — Setting Up Test Data

`TestEntityManager` wraps the real `EntityManager` but adds test-friendly helpers:

```java
// persistAndFlush: save to DB and flush to ensure it's visible to subsequent queries
User alice = em.persistAndFlush(
    User.builder()
        .name("Alice")
        .email("alice@test.com")
        .passwordHash("hashed")
        .role(Role.USER)
        .build()
);

Project project = em.persistAndFlush(
    Project.builder()
        .name("TaskForge")
        .owner(alice)
        .build()
);

// em.clear() — detach all entities to force a real DB query (not from cache)
em.clear();
```

---

## Repository Query Tests

```java
@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TaskRepository taskRepository;

    @Test
    void findByProjectId_shouldReturnTasksForProject() {
        // Arrange
        User owner = em.persistAndFlush(makeUser("owner@test.com"));
        Project project = em.persistAndFlush(makeProject("TaskForge", owner));
        Task task1 = em.persistAndFlush(makeTask("Bug fix", TaskStatus.TODO, project, owner));
        Task task2 = em.persistAndFlush(makeTask("Feature", TaskStatus.IN_PROGRESS, project, owner));
        em.clear();   // detach all — forces real query

        // Act
        List<Task> tasks = taskRepository.findByProjectId(project.getId());

        // Assert
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::getTitle)
            .containsExactlyInAnyOrder("Bug fix", "Feature");
    }

    @Test
    void findByProjectIdAndStatus_shouldFilterCorrectly() {
        // Arrange
        User owner = em.persistAndFlush(makeUser("filter@test.com"));
        Project project = em.persistAndFlush(makeProject("Filter Project", owner));
        em.persistAndFlush(makeTask("T1", TaskStatus.TODO, project, owner));
        em.persistAndFlush(makeTask("T2", TaskStatus.DONE, project, owner));
        em.persistAndFlush(makeTask("T3", TaskStatus.TODO, project, owner));
        em.clear();

        // Act
        List<Task> todoTasks = taskRepository.findByProjectIdAndStatus(
            project.getId(), TaskStatus.TODO);

        // Assert
        assertThat(todoTasks).hasSize(2);
        assertThat(todoTasks).allMatch(t -> t.getStatus() == TaskStatus.TODO);
    }

    @Test
    void findByProjectId_pageable_shouldReturnCorrectPage() {
        // Arrange
        User owner = em.persistAndFlush(makeUser("page@test.com"));
        Project project = em.persistAndFlush(makeProject("Pageable", owner));
        for (int i = 1; i <= 15; i++) {
            em.persistAndFlush(makeTask("Task " + i, TaskStatus.TODO, project, owner));
        }
        em.clear();

        // Act
        Page<Task> page = taskRepository.findByProjectId(project.getId(), PageRequest.of(0, 10));

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(10);
    }

    // ────── Helpers ──────

    private User makeUser(String email) {
        return User.builder()
            .name("Test User")
            .email(email)
            .passwordHash("$2a$12$hashed")
            .role(Role.USER)
            .build();
    }

    private Project makeProject(String name, User owner) {
        return Project.builder()
            .name(name)
            .owner(owner)
            .build();
    }

    private Task makeTask(String title, TaskStatus status, Project project, User creator) {
        return Task.builder()
            .title(title)
            .status(status)
            .priority(Priority.MEDIUM)
            .project(project)
            .creator(creator)
            .build();
    }
}
```

---

## Testing Custom @Query Methods

```java
@Test
void findByAssigneeIdAndStatus_shouldUseCustomQuery() {
    // Arrange
    User owner = em.persistAndFlush(makeUser("owner@repo.com"));
    User assignee = em.persistAndFlush(makeUser("assignee@repo.com"));
    Project project = em.persistAndFlush(makeProject("Repo Project", owner));

    Task assigned = Task.builder()
        .title("Assigned task")
        .status(TaskStatus.IN_PROGRESS)
        .priority(Priority.HIGH)
        .project(project)
        .creator(owner)
        .assignee(assignee)       // JPQL custom query uses this field
        .build();
    em.persistAndFlush(assigned);

    Task notAssigned = makeTask("Not assigned", TaskStatus.TODO, project, owner);
    em.persistAndFlush(notAssigned);
    em.clear();

    // Act
    List<Task> assigneeTasks = taskRepository
        .findByAssigneeIdAndStatus(assignee.getId(), TaskStatus.IN_PROGRESS);

    // Assert
    assertThat(assigneeTasks).hasSize(1);
    assertThat(assigneeTasks.get(0).getTitle()).isEqualTo("Assigned task");
}
```

---

## H2 Compatibility with PostgreSQL Syntax

H2 supports a `MODE=PostgreSQL` setting for compatibility. Set it in `application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
```

Known differences to watch for:
- H2 doesn't support all PostgreSQL-specific functions
- Enum handling may differ slightly
- Use `spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.H2Dialect` for tests

---

## @DataJpaTest with TestEntityManager vs direct save()

Both approaches work. Use what fits the test:

```java
// Option A: TestEntityManager (more control, explicit flush)
User user = em.persistAndFlush(User.builder()...build());
em.clear();

// Option B: repository.save() (simpler, but shares transaction)
User user = userRepository.save(User.builder()...build());
userRepository.flush();   // ensure visible before query
```

---

## Testing Relationship Loading

```java
@Test
void findProjectById_shouldLazyLoadMembers() {
    User owner = em.persistAndFlush(makeUser("owner@lazy.com"));
    User member = em.persistAndFlush(makeUser("member@lazy.com"));
    Project project = Project.builder().name("Lazy Test").owner(owner).build();
    project.addMember(member);
    em.persistAndFlush(project);
    em.clear();

    // Act
    Optional<Project> found = projectRepository.findById(project.getId());

    // Assert
    assertThat(found).isPresent();
    // Accessing members inside the same transaction is fine (no LazyInitializationException)
    assertThat(found.get().getMembers()).hasSize(1);
}
```

---

## Capstone Connection

**Repository tests to write:**

| Repository | Test cases |
|-----------|-----------|
| `UserRepository` | findByEmail success, findByEmail not found |
| `ProjectRepository` | findByOwnerId, count tasks per project |
| `TaskRepository` | findByProjectId, findByProjectId+Status, pagination, findByAssignee |
| `CommentRepository` | findByTaskId ordered by createdAt, count by taskId |

---

## Next

[Exercises](./exercises/README.md) — build a comprehensive test suite for TaskForge.
