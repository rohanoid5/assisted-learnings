# 02 — Entity Lifecycle

## The Four Entity States

Every JPA entity exists in one of four states. Understanding these states explains *when* Hibernate writes to the database.

```
                    new Task()
                        │
                        ▼
            ┌──────────────────────┐
            │      TRANSIENT       │  Not known to Hibernate, no DB identity
            └──────────────────────┘
                        │
          em.persist()  │  taskRepository.save() on new entity
                        ▼
            ┌──────────────────────┐
            │       MANAGED        │  Hibernate tracks changes → auto-writes on commit
            └──────────────────────┘
           │                        │
  em.detach()                em.remove()
  tx commits (auto)          taskRepository.delete()
           │                        │
           ▼                        ▼
  ┌──────────────────┐    ┌──────────────────────┐
  │    DETACHED      │    │       REMOVED         │  DELETE queued
  └──────────────────┘    └──────────────────────┘
           │
   em.merge()
           │
           ▼
      MANAGED again
```

---

## State 1: Transient

An object just created with `new` — Hibernate has no idea it exists.

```java
Task task = new Task();       // TRANSIENT
task.setTitle("Fix bug #42");
task.setStatus(TaskStatus.TODO);
// At this point: no id, no DB row, Hibernate doesn't track it
```

---

## State 2: Managed

An entity becomes Managed when:
- You call `em.persist(entity)` (new entity)
- You retrieve it via `em.find()` or a query
- You call `em.merge(detachedEntity)`

**Key property:** Hibernate tracks all field changes on managed entities. At transaction commit, it runs a **dirty check** and automatically generates `UPDATE` SQL for any changed fields.

```java
@Transactional
public void updateTitle(Long id, String newTitle) {
    Task task = taskRepository.findById(id).orElseThrow(); // MANAGED
    task.setTitle(newTitle);   // no explicit save() needed!
    // Hibernate dirty-checks at commit → generates UPDATE SQL
}
```

This is called **automatic dirty checking** — the biggest "magic" of Hibernate.

**Node.js TypeORM equiv:**
```typescript
// TypeORM requires explicit save()
const task = await taskRepository.findOne({ where: { id } });
task.title = newTitle;
await taskRepository.save(task); // must call save()

// Spring Data JPA equivalent — NO save() needed inside @Transactional:
// task.setTitle(newTitle);
// // That's it. Hibernate handles the UPDATE.
```

---

## State 3: Detached

An entity becomes Detached when:
- The `EntityManager` (persistence context) closes — e.g., transaction ends
- You explicitly call `em.detach(entity)` or `em.clear()`

Detached entities are no longer tracked. Changes won't auto-persist.

```java
@Transactional
public Task findById(Long id) {
    return taskRepository.findById(id).orElseThrow();
    // entity is MANAGED inside this transaction
}
// ← transaction ends here → entity returned is now DETACHED

// Calling setTitle() on the returned Task will NOT update the DB
task.setTitle("New title"); // silently ignored!
```

**This is why returning entities from service methods can be dangerous.** Use DTOs instead — a DTO is just a plain Java record, it has no lifecycle.

### Merging a Detached Entity

```java
@Transactional
public Task update(Task detachedTask) {
    Task managed = em.merge(detachedTask);  // re-attaches, copies state
    // managed is now MANAGED — changes will persist
    return managed;
}
```

In Spring Data, `repository.save(existingEntity)` calls `em.merge()` when the entity already has an id.

---

## State 4: Removed

```java
@Transactional
public void deleteTask(Long id) {
    Task task = taskRepository.findById(id).orElseThrow();
    taskRepository.delete(task);  // → em.remove(task) → DELETE queued
    // entity state: REMOVED
    // DELETE SQL runs at transaction commit
}
```

---

## The Persistence Context (First-Level Cache)

The **persistence context** is the "unit of work" — a short-lived, transaction-scoped cache of managed entities. Within a transaction:

```java
@Transactional
public void example(Long id) {
    Task task1 = taskRepository.findById(id).orElseThrow();  // SELECT SQL
    Task task2 = taskRepository.findById(id).orElseThrow();  // NO SQL — cache hit!
    
    System.out.println(task1 == task2); // true — same instance!
}
```

Hibernate guarantees **identity equality** — the same entity id always returns the same Java object within a persistence context. This is the **first-level cache**; it's always on and scoped to the current transaction.

**Second-level cache** (e.g., Ehcache, Caffeine) is cross-transaction and opt-in — not needed for TaskForge.

---

## Flushing: When Does SQL Actually Execute?

Hibernate buffers SQL and sends it to the DB in a **flush**. Flush happens:

1. At transaction commit (always)
2. Before a query (to ensure query sees the latest changes)
3. When you call `em.flush()` explicitly

```java
@Transactional
public void demo() {
    Task task = new Task();
    task.setTitle("Test");
    taskRepository.save(task);
    // No SQL yet!
    
    List<Task> all = taskRepository.findAll();
    // Hibernate flushes before this query → INSERT runs first
    // Now the findAll() sees the new task
    
    task.setTitle("Changed");
    // No SQL yet!
    
    // Transaction commits → UPDATE runs
}
```

**Flush modes:**

| Mode | Behavior |
|------|----------|
| `AUTO` | Flush before queries and at commit (default) |
| `COMMIT` | Flush only at commit — queries may see stale data |
| `ALWAYS` | Flush before every query |
| `NEVER` | Only manual `em.flush()` |

For most apps, leave it as `AUTO`.

---

## @Transactional and Session Scope

In Spring, the persistence context lives for the duration of one `@Transactional` method. This has a critical implication: **lazy associations can only be loaded while the session is open**.

```java
@Service
public class TaskService {

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        Task task = taskRepository.findById(id).orElseThrow();
        // session is open here → lazy associations load fine
        String projectName = task.getProject().getName(); // OK
        return toResponse(task); // convert to DTO before session closes
    }
}

// WRONG approach:
public Task findByIdRaw(Long id) {
    return taskRepository.findById(id).orElseThrow();
    // session closes when @Transactional method returns
}

// In controller:
Task task = taskService.findByIdRaw(id);
task.getProject().getName(); // ← LazyInitializationException!
// The session is gone — can't load project anymore
```

This is why:
1. Services should return DTOs, not entities
2. `spring.jpa.open-in-view` should be `false` for APIs (it masks this error by keeping the session open for the whole HTTP request)

---

## getReferenceById vs findById

```java
// findById: runs SELECT immediately, returns Optional<Task>
// Use when you need the entity's data
Task task = taskRepository.findById(id).orElseThrow();

// getReferenceById: returns a proxy with just the id, NO SELECT
// Use when you only need the foreign key reference (for associations)
User assignee = userRepository.getReferenceById(assigneeId);
task.setAssignee(assignee); // sets FK column, no SELECT needed
```

`getReferenceById()` is a significant performance optimization when setting foreign key relationships — you avoid an unnecessary SELECT just to get an entity reference.

---

## Try It Yourself

**Observe dirty checking:**

```java
// src/test/java/com/taskforge/EntityLifecycleTest.java
@SpringBootTest
@Transactional
class EntityLifecycleTest {

    @Autowired TaskRepository taskRepository;
    @Autowired EntityManager em;

    @Test
    void testDirtyChecking() {
        // Create
        Task task = Task.builder()
            .title("Original")
            .status(TaskStatus.TODO)
            .priority(Priority.MEDIUM)
            .build();
        taskRepository.save(task);
        em.flush();      // force the INSERT

        // Modify — no save() call
        task.setTitle("Modified");
        em.flush();      // force the UPDATE

        // Verify
        em.clear();      // clear cache, force fresh SELECT
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals("Modified", reloaded.getTitle());
    }
}
```

Enable SQL logging (`spring.jpa.show-sql: true`) and run this test. You'll see:
1. `insert into tasks ...`
2. `update tasks set title='Modified' ...`

No explicit `save()` for the update — dirty checking did it.

---

## Capstone Connection

In TaskForge's service layer, every write method is `@Transactional`. Read methods are `@Transactional(readOnly = true)`. The service always returns DTOs — entities never escape the transaction boundary.

```java
@Transactional
public TaskResponse create(CreateTaskRequest request, UserPrincipal currentUser) {
    // TRANSIENT
    Task task = Task.builder()
        .title(request.title())
        .status(TaskStatus.TODO)
        .project(projectRepository.getReferenceById(request.projectId())) // no SELECT
        .createdBy(userRepository.getReferenceById(currentUser.getId()))   // no SELECT
        .build();

    // MANAGED (after persist)
    Task saved = taskRepository.save(task);
    
    // Return DTO — entity stays within this transaction boundary
    return toResponse(saved);
    // Transaction commits → INSERT runs → session closes
}
```

**Next:** [03 — Relationships](./03-relationships.md) — mapping the connections between Task, Project, User, and Comment entities.
