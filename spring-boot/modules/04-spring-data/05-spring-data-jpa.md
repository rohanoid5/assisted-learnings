# 05 — Spring Data JPA

## What Is Spring Data JPA?

Spring Data JPA provides **repository abstractions** over JPA/Hibernate. You define interfaces; Spring generates the implementations at runtime.

**Node.js mental model:**
```typescript
// TypeORM: you write the repository or use DataSource methods
const tasks = await taskRepository.find({ where: { status: 'TODO' } });

// Spring Data JPA: define an interface, Spring generates the implementation
List<Task> findByStatus(TaskStatus status);  // Spring writes this for you
```

---

## JpaRepository\<T, ID\>

```java
// That's it — just extend JpaRepository
public interface TaskRepository extends JpaRepository<Task, Long> {
    // 50+ base methods now available, free:
    // save(), saveAll(), findById(), findAll(), count(), delete(), deleteById(), existsById()...
}
```

`JpaRepository<Task, Long>` means: entity type `Task`, primary key type `Long`.

**The inheritance chain:**
```
JpaRepository
    └── PagingAndSortingRepository  (findAll with Pageable/Sort)
            └── CrudRepository       (save, find, delete)
                    └── Repository   (marker interface)
```

**Key methods you get for free:**

```java
// Save (INSERT if new, UPDATE if existing)
Task saved = taskRepository.save(task);
List<Task> savedAll = taskRepository.saveAll(List.of(t1, t2, t3));

// Find
Optional<Task> task = taskRepository.findById(42L);
List<Task> all = taskRepository.findAll();
List<Task> some = taskRepository.findAllById(List.of(1L, 2L, 3L));
boolean exists = taskRepository.existsById(42L);
long count = taskRepository.count();

// Delete
taskRepository.deleteById(42L);
taskRepository.delete(task);
taskRepository.deleteAll(List.of(t1, t2));
taskRepository.deleteAllById(List.of(1L, 2L));

// Paging and sorting
Page<Task> page = taskRepository.findAll(PageRequest.of(0, 20, Sort.by("createdAt").descending()));
```

---

## Derived Query Methods

Spring Data parses method names and generates JPQL automatically:

```java
public interface TaskRepository extends JpaRepository<Task, Long> {

    // SELECT t FROM Task t WHERE t.status = ?
    List<Task> findByStatus(TaskStatus status);

    // SELECT t FROM Task t WHERE t.project.id = ?
    List<Task> findByProjectId(Long projectId);

    // SELECT t FROM Task t WHERE t.assignee.id = ?
    List<Task> findByAssigneeId(Long userId);

    // SELECT t FROM Task t WHERE t.status = ? AND t.project.id = ?
    List<Task> findByStatusAndProjectId(TaskStatus status, Long projectId);

    // SELECT t FROM Task t WHERE t.priority IN (?, ?)
    List<Task> findByPriorityIn(List<Priority> priorities);

    // COUNT query
    long countByProjectId(Long projectId);

    // EXISTS check
    boolean existsByProjectIdAndStatus(Long projectId, TaskStatus status);

    // DELETE
    void deleteByProjectId(Long projectId);

    // With sorting
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    // LIKE query (case-insensitive)
    List<Task> findByTitleContainingIgnoreCase(String keyword);
}
```

**Derived query keyword reference:**

| Keyword | Example | JPQL fragment |
|---------|---------|---------------|
| `And` | `findByStatusAndPriority` | `WHERE status = ? AND priority = ?` |
| `Or` | `findByStatusOrPriority` | `WHERE status = ? OR priority = ?` |
| `Is`, `Equals` | `findByStatusIs` | `WHERE status = ?` |
| `Not` | `findByStatusNot` | `WHERE status <> ?` |
| `In` | `findByStatusIn(Collection)` | `WHERE status IN (...)` |
| `NotIn` | `findByStatusNotIn` | `WHERE status NOT IN (...)` |
| `IsNull` | `findByAssigneeIsNull` | `WHERE assignee IS NULL` |
| `IsNotNull` | `findByAssigneeIsNotNull` | `WHERE assignee IS NOT NULL` |
| `Like` | `findByTitleLike` | `WHERE title LIKE ?` |
| `Containing` | `findByTitleContaining` | `WHERE title LIKE %?%` |
| `StartingWith` | `findByTitleStartingWith` | `WHERE title LIKE ?%` |
| `GreaterThan` | `findByCreatedAtGreaterThan` | `WHERE created_at > ?` |
| `Between` | `findByCreatedAtBetween` | `WHERE created_at BETWEEN ? AND ?` |
| `OrderBy` | `findByProjectOrderByCreatedAtDesc` | `ORDER BY created_at DESC` |
| `Top`, `First` | `findTop5ByProjectId` | `LIMIT 5` |

---

## @Query — Custom JPQL

For complex queries that don't fit derived method names:

```java
public interface TaskRepository extends JpaRepository<Task, Long> {

    // JPQL (Java Persistence Query Language) — uses entity/field names, not table/column names
    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.status = :status")
    List<Task> findByProjectAndStatus(
        @Param("projectId") Long projectId,
        @Param("status") TaskStatus status
    );

    // JOIN FETCH — solves N+1 for a specific query
    @Query("SELECT t FROM Task t " +
           "LEFT JOIN FETCH t.assignee " +
           "LEFT JOIN FETCH t.createdBy " +
           "WHERE t.id = :id")
    Optional<Task> findByIdWithDetails(@Param("id") Long id);

    // @EntityGraph alternative to JOIN FETCH
    @EntityGraph(attributePaths = {"assignee", "createdBy", "project"})
    Optional<Task> findDetailedById(Long id);

    // Aggregate query
    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId AND t.status = 'DONE'")
    long countCompletedByProject(@Param("projectId") Long projectId);

    // DTO projection — only select needed fields (fastest)
    @Query("SELECT new com.taskforge.dto.response.TaskSummary(t.id, t.title, t.status, t.priority) " +
           "FROM Task t WHERE t.assignee.id = :userId")
    List<TaskSummary> findSummariesByAssignee(@Param("userId") Long userId);
}
```

**Native SQL queries** (when you need DB-specific features):
```java
@Query(value = "SELECT * FROM tasks WHERE created_at > NOW() - INTERVAL '7 days'",
       nativeQuery = true)
List<Task> findRecentTasks();
```

---

## Paging and Sorting

```java
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Returns a Page<T> — includes total count, total pages, content
    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    // Just returns a Slice<T> — no total count (faster for "load more" UI)
    Slice<Task> findByStatus(TaskStatus status, Pageable pageable);
}
```

```java
// In Service:
public Page<TaskResponse> findAll(Long projectId, int page, int size, String sortBy) {
    Pageable pageable = PageRequest.of(
        page,                                   // 0-based page number
        size,                                   // items per page
        Sort.by(Sort.Direction.DESC, "createdAt") // sort order
    );
    
    return taskRepository.findByProjectId(projectId, pageable)
        .map(this::toResponse); // Page.map() transforms each element
}
```

**Controller:**
```java
@GetMapping
public ResponseEntity<Page<TaskResponse>> getTasks(
        @RequestParam Long projectId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(taskService.findAll(projectId, page, size));
}
```

**Page JSON response:**
```json
{
  "content": [...],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,       // current page
  "size": 20,
  "first": true,
  "last": false
}
```

---

## Projections — Returning Less Data

Three approaches to avoid loading full entities:

**1. Interface Projection:**
```java
// Define interface with getters
public interface TaskSummary {
    Long getId();
    String getTitle();
    TaskStatus getStatus();
}

// Spring generates SQL that only selects those columns
List<TaskSummary> findByProjectId(Long projectId);
```

**2. DTO (Class) Projection via @Query:**
```java
public record TaskSummary(Long id, String title, TaskStatus status) {}

@Query("SELECT new com.taskforge.dto.response.TaskSummary(t.id, t.title, t.status) FROM Task t WHERE t.project.id = :projectId")
List<TaskSummary> findSummariesByProject(@Param("projectId") Long projectId);
```

**3. @Tuple / native queries for complex aggregations:**
```java
@Query(value = "SELECT status, COUNT(*) as count FROM tasks WHERE project_id = :projectId GROUP BY status",
       nativeQuery = true)
List<Object[]> countByStatus(@Param("projectId") Long projectId);
```

---

## Modifying Queries

For UPDATE and DELETE @Query operations, add `@Modifying`:

```java
@Modifying
@Query("UPDATE Task t SET t.status = :status WHERE t.project.id = :projectId")
int bulkUpdateStatus(@Param("projectId") Long projectId, @Param("status") TaskStatus status);

@Modifying
@Query("DELETE FROM Task t WHERE t.project.id = :projectId AND t.status = 'DONE'")
void deleteCompletedTasksByProject(@Param("projectId") Long projectId);
```

These must be called from within a `@Transactional` method.

---

## Full TaskForge Repositories

```java
// src/main/java/com/taskforge/repository/UserRepository.java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

// src/main/java/com/taskforge/repository/ProjectRepository.java
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.id = :userId OR p.owner.id = :userId")
    List<Project> findByMemberOrOwner(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"owner", "members"})
    Optional<Project> findWithMembersById(Long id);
}

// src/main/java/com/taskforge/repository/TaskRepository.java
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    List<Task> findByProjectId(Long projectId);
    List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status);
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"assignee", "createdBy"})
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    
    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.assignee LEFT JOIN FETCH t.createdBy WHERE t.id = :id")
    Optional<Task> findByIdWithDetails(@Param("id") Long id);
    
    long countByProjectId(Long projectId);
    
    @Modifying
    @Query("UPDATE Task t SET t.assignee = null WHERE t.assignee.id = :userId")
    void unassignAllByUser(@Param("userId") Long userId);
}

// src/main/java/com/taskforge/repository/CommentRepository.java
public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    List<Comment> findByTaskIdOrderByCreatedAtAsc(Long taskId);
    
    @EntityGraph(attributePaths = {"author"})
    List<Comment> findByTaskId(Long taskId);
    
    void deleteByTaskId(Long taskId);
}
```

---

## Try It Yourself

Connect PostgreSQL and test your repositories:

```java
// src/test/java/com/taskforge/repository/TaskRepositoryTest.java
@DataJpaTest          // loads only JPA layer — fast, uses H2 by default
class TaskRepositoryTest {

    @Autowired TaskRepository taskRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired UserRepository userRepository;

    @Test
    void shouldFindTasksByStatus() {
        // ... setup user, project, tasks
        List<Task> todoTasks = taskRepository.findByProjectIdAndStatus(project.getId(), TaskStatus.TODO);
        assertEquals(2, todoTasks.size());
    }
    
    @Test
    void shouldPageTasks() {
        // ... create 25 tasks
        Page<Task> page = taskRepository.findByProjectId(projectId, PageRequest.of(0, 10));
        assertEquals(10, page.getContent().size());
        assertEquals(25, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
    }
}
```

---

## Capstone Connection

All 4 repositories are now defined. The services from Module 3 (stub implementations) are replaced with real JPA queries. The `TaskService.create()` method now calls `taskRepository.save(task)`, which inserts into PostgreSQL.

**Next:** [06 — Spring Data MongoDB](./06-spring-data-mongodb.md) — overview of the document store alternative.
