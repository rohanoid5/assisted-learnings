# 04 — Transactions

## What Is a Transaction?

A **transaction** is a unit of work that either fully succeeds or fully fails — no partial state.

**The classic bank transfer example:**
```
Transfer $100 from Account A to Account B:
1. Deduct $100 from A
2. Add $100 to B

If step 2 fails, step 1 must be rolled back.
Without transactions: money disappears.
With transactions: both steps succeed or neither does.
```

Databases guarantee **ACID** properties within a transaction:

| Property | Meaning |
|----------|---------|
| **Atomicity** | All or nothing — either all operations commit or all roll back |
| **Consistency** | DB constraints are never violated (FK, NOT NULL, UNIQUE) |
| **Isolation** | Concurrent transactions don't interfere with each other |
| **Durability** | Committed changes survive crashes |

**Node.js mental model:**
```javascript
// TypeORM
await dataSource.transaction(async (manager) => {
    await manager.save(newTask);
    await manager.update(Project, projectId, { taskCount: taskCount + 1 });
    // if either fails, both roll back
});
```

In Spring, `@Transactional` handles this declaratively.

---

## @Transactional Basics

```java
@Service
public class TaskService {

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, UserPrincipal currentUser) {
        // Everything in this method runs in ONE transaction:
        Task task = Task.builder()...build();
        taskRepository.save(task);  // INSERT
        
        // If this throws, the INSERT above rolls back:
        projectRepository.incrementTaskCount(request.projectId()); // UPDATE
        
        return toResponse(task);
    }
}
```

Spring uses an **AOP proxy** to wrap your method in transaction management code (begin → execute → commit/rollback). This is why `@Transactional` only works on `public` methods and only when called from *outside* the class (no self-invocation — same limitation as AOP in Module 1).

---

## readOnly = true

```java
@Service
@Transactional(readOnly = true)  // Class-level default: all methods are read-only
public class TaskService {

    public List<TaskResponse> findAll() {
        // Read-only transaction: Hibernate skips dirty checking, DB can use read replicas
        return taskRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional  // Override: this method is NOT read-only (can write)
    public TaskResponse create(CreateTaskRequest req, UserPrincipal user) {
        // Write operations here
    }
}
```

**Benefits of readOnly = true:**
- Hibernate skips the dirty check (no need to compare entity state at commit)
- DB driver may use read-replica connections
- Some ORMs skip writing flush entirely

**Pattern:** Put `@Transactional(readOnly = true)` at the class level, then override with `@Transactional` for write methods.

---

## Rollback Rules

By default, Spring only rolls back on **unchecked exceptions** (RuntimeException and its subclasses). Checked exceptions do NOT trigger rollback.

```java
// These WILL roll back the transaction:
throw new RuntimeException("oops");
throw new ResourceNotFoundException("Task", id);   // extends RuntimeException
throw new IllegalArgumentException("bad input");

// These will NOT roll back (checked exceptions):
throw new IOException("file not found");
throw new SQLException("db error");                // also caught by Spring anyway
```

**Override rollback rules:**
```java
@Transactional(rollbackFor = Exception.class)      // roll back on ALL exceptions
@Transactional(noRollbackFor = ValidationException.class) // don't roll back on this
```

**TaskForge uses:** All custom exceptions extend `RuntimeException`, so default rollback behavior is correct. No override needed.

---

## Transaction Propagation

Propagation defines what happens when a `@Transactional` method is called from within an existing transaction.

```
ServiceA.methodA() ──(@Transactional)──► ServiceB.methodB() ──(@Transactional)──► ???
```

| Propagation | Behavior |
|-------------|----------|
| `REQUIRED` (default) | Join existing transaction, or create new one |
| `REQUIRES_NEW` | Always create a new transaction (suspends existing) |
| `SUPPORTS` | Join if exists, but don't require one |
| `MANDATORY` | Must have an existing transaction; throw if none |
| `NOT_SUPPORTED` | Suspend existing transaction, run without one |
| `NEVER` | Must NOT have a transaction; throw if one exists |
| `NESTED` | Nested within existing transaction (savepoint) |

**The most important ones for TaskForge:**

```java
// Default — method joins caller's transaction
@Transactional(propagation = Propagation.REQUIRED)
public TaskResponse createTask(...) { ... }

// Audit logging — must always persist even if the outer transaction rolls back
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAuditEvent(String action, Long userId) {
    auditLogRepository.save(new AuditLog(action, userId, Instant.now()));
    // runs in its own transaction, commits independently
}
```

---

## Isolation Levels

Isolation controls what your transaction can see from concurrent transactions.

| Level | Dirty Read | Non-repeatable Read | Phantom Read |
|-------|-----------|--------------------|----|
| `READ_UNCOMMITTED` | ✅ allowed | ✅ allowed | ✅ allowed |
| `READ_COMMITTED` (PostgreSQL default) | ❌ prevented | ✅ allowed | ✅ allowed |
| `REPEATABLE_READ` | ❌ | ❌ | ✅ allowed |
| `SERIALIZABLE` | ❌ | ❌ | ❌ prevented |

- **Dirty Read:** Reading uncommitted changes from another transaction
- **Non-repeatable Read:** Same row returns different data if re-read (another tx committed in between)
- **Phantom Read:** A range query returns different rows if re-run (another tx inserted/deleted)

```java
@Transactional(isolation = Isolation.READ_COMMITTED)  // typically the default
public TaskResponse findById(Long id) { ... }
```

**For TaskForge:** PostgreSQL's default `READ_COMMITTED` is sufficient. Only override for specific high-concurrency operations.

---

## Common Transactional Pitfalls

### Pitfall 1: Self-Invocation (Most Common Bug)

```java
@Service
public class TaskService {

    @Transactional
    public void processTask(Long id) {
        // This calls a @Transactional method on the SAME bean instance
        this.updateStatus(id, TaskStatus.IN_PROGRESS); // ← WRONG!
        // @Transactional is IGNORED — no new transaction created
    }

    @Transactional
    public void updateStatus(Long id, TaskStatus status) { ... }
}
```

**Fix:** Inject the service into itself (create a separate bean), or restructure to avoid self-calls.

### Pitfall 2: @Transactional on Private Methods

```java
// @Transactional on private method is silently IGNORED
@Transactional   // ← this does nothing
private void helper() { ... }
```

Spring's AOP proxy can only intercept public method calls.

### Pitfall 3: Storing Entities in Long-Lived Collections

```java
// DataInitializer that runs at startup
@Component
public class DataInitializer {
    private final List<Task> cachedTasks = new ArrayList<>();

    @PostConstruct
    @Transactional
    public void init() {
        Task task = taskRepository.save(new Task("Init task", ...));
        cachedTasks.add(task); // DETACHED after this method returns
    }
    
    public List<Task> getCachedTasks() {
        return cachedTasks; // These are DETACHED — lazy loading throws LazyInitializationException
    }
}
```

**Fix:** Cache DTOs, not entities.

### Pitfall 4: Not Joining the Parent Transaction

```java
// If NotificationService has @Transactional(propagation = REQUIRES_NEW):
@Transactional
public TaskResponse create(...) {
    Task task = taskRepository.save(...);
    notificationService.notify(task); // creates NEW transaction
    // If this throws, the outer transaction rolls back but notification was already committed
}
```

Understand propagation before using `REQUIRES_NEW`.

---

## Try It Yourself

**Test rollback behavior:**

```java
@SpringBootTest
class TransactionTest {

    @Autowired TaskRepository taskRepository;
    @Autowired TaskService taskService;

    @Test
    void testRollback() {
        long countBefore = taskRepository.count();

        try {
            taskService.createTaskAndThrow(); // should throw RuntimeException
        } catch (Exception e) {
            // expected
        }

        long countAfter = taskRepository.count();
        assertEquals(countBefore, countAfter, "Task should have been rolled back");
    }
}
```

Add to TaskService:
```java
@Transactional
public void createTaskAndThrow() {
    taskRepository.save(Task.builder()
        .title("Rolling back")
        .status(TaskStatus.TODO)
        .priority(Priority.LOW)
        .build());
    throw new RuntimeException("Intentional rollback");
}
```

---

## Capstone Connection

In TaskForge, every service class follows this pattern:

```java
@Service
@Transactional(readOnly = true)  // ← class-level default
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<ProjectResponse> findByMember(UserPrincipal user) { ... } // read-only

    @Transactional  // override for writes
    public ProjectResponse create(CreateProjectRequest req, UserPrincipal user) { ... }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest req, UserPrincipal user) { ... }

    @Transactional
    public void delete(Long id, UserPrincipal user) { ... }
}
```

**Next:** [05 — Spring Data JPA](./05-spring-data-jpa.md) — JpaRepository, derived queries, @Query, and Pageable.
