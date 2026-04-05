# 01 — Hibernate Basics

## What Is an ORM?

An **Object-Relational Mapper** bridges the gap between object-oriented Java and relational databases.

Without an ORM, you'd write raw SQL and manually map rows to objects:
```java
// Without ORM — raw JDBC (painful)
ResultSet rs = stmt.executeQuery("SELECT * FROM tasks WHERE id = 42");
Task task = new Task();
task.setId(rs.getLong("id"));
task.setTitle(rs.getString("title"));
task.setStatus(TaskStatus.valueOf(rs.getString("status")));
// ... 15 more fields
```

With an ORM:
```java
// With JPA/Hibernate
Task task = entityManager.find(Task.class, 42L);  // one line
```

**Node.js mental model:**

| Node.js | Java |
|---------|------|
| TypeORM / Prisma | Hibernate (the ORM) |
| TypeORM's `DataSource` | JPA's `EntityManager` |
| Prisma schema (`model Task {}`) | JPA `@Entity` class |
| TypeORM migration | Flyway / Liquibase migration |
| `find()`, `save()`, `delete()` | `find()`, `persist()`, `remove()` |
| TypeORM `Repository` | Spring Data `JpaRepository` |

---

## JPA vs Hibernate: The Distinction

**JPA** (Jakarta Persistence API) is a **specification** — a set of interfaces and annotations defined in `jakarta.persistence.*`.

**Hibernate** is an **implementation** of JPA — the actual code that runs SQL against your database.

```
Your code → JPA annotations/interfaces → Hibernate → JDBC → PostgreSQL
```

`spring-boot-starter-data-jpa` includes Hibernate as the default JPA provider. You write code to the JPA spec (so it's portable), and Hibernate executes it.

**Analogy:** JPA is like JDBC's `javax.sql.DataSource` interface. Hibernate is like HikariCP — the implementation you actually use.

---

## Your First @Entity

An **entity** is a Java class mapped to a database table.

```java
// src/main/java/com/taskforge/domain/Task.java
package com.taskforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto-increment in PostgreSQL
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)        // store "TODO", not ordinal integer
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

**Annotation breakdown:**

| Annotation | Purpose |
|-----------|---------|
| `@Entity` | Marks this class as a JPA entity (mapped to a table) |
| `@Table(name = "tasks")` | Specifies the table name (defaults to class name if omitted) |
| `@Id` | Primary key field |
| `@GeneratedValue(IDENTITY)` | DB auto-increment (`SERIAL` / `BIGSERIAL` in PostgreSQL) |
| `@Column` | Column mapping (name, constraints) |
| `@Enumerated(EnumType.STRING)` | Store enum as string, not integer |
| `@CreationTimestamp` | Hibernate sets this once on INSERT |
| `@UpdateTimestamp` | Hibernate updates this on every UPDATE |

---

## EntityManager: The Core API

`EntityManager` is the JPA interface for interacting with the persistence context. Spring Data's `JpaRepository` wraps this — you rarely use it directly, but understanding it demystifies the magic:

```java
// Direct EntityManager usage (rare, but educational)
@Service
@RequiredArgsConstructor
public class LowLevelTaskService {

    private final EntityManager em;

    @Transactional
    public Task save(Task task) {
        em.persist(task);     // INSERT
        return task;
    }

    public Task findById(Long id) {
        return em.find(Task.class, id);  // SELECT * WHERE id = ?
    }

    @Transactional
    public void update(Task task) {
        em.merge(task);       // UPDATE
    }

    @Transactional
    public void delete(Long id) {
        Task task = em.find(Task.class, id);
        em.remove(task);      // DELETE
    }
}
```

In practice, `JpaRepository` handles all of this. EntityManager knowledge matters when writing custom queries or performance-tuning.

---

## The N+1 Problem

The most notorious ORM performance pitfall.

**Scenario:** Fetch 100 projects, then for each project, access its list of tasks.

```java
List<Project> projects = projectRepository.findAll(); // 1 query
for (Project p : projects) {
    // LAZY by default: each access triggers a query!
    int taskCount = p.getTasks().size(); // 100 queries!
}
// Total: 101 queries for 100 projects → N+1
```

**Solutions:**

**1. Eager JOIN FETCH (best for small datasets):**
```java
@Query("SELECT p FROM Project p LEFT JOIN FETCH p.tasks WHERE p.owner.id = :ownerId")
List<Project> findByOwnerWithTasks(@Param("ownerId") Long ownerId);
```

**2. @EntityGraph (declarative join fetch):**
```java
@EntityGraph(attributePaths = {"tasks", "members"})
List<Project> findByOwnerId(Long ownerId);
```

**3. DTO projection (fastest — only select what you need):**
```java
@Query("SELECT new com.taskforge.dto.ProjectSummary(p.id, p.name, COUNT(t)) " +
       "FROM Project p LEFT JOIN p.tasks t GROUP BY p.id, p.name")
List<ProjectSummary> findAllSummaries();
```

**Node.js / TypeORM equiv:**
```typescript
// N+1: eager loading in TypeORM
const projects = await projectRepository.find({
    relations: ['tasks']  // one JOIN query instead of N queries
});
```

---

## Hibernate DDL Auto

In development, Hibernate can auto-generate your database schema from entities:

```yaml
# application-dev.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # drop + create on startup, drop on shutdown
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

DDL auto modes:

| Mode | Behavior | Use when |
|------|----------|----------|
| `none` | Does nothing | Production (use Flyway) |
| `validate` | Validates schema matches entities | Production |
| `update` | Adds missing columns/tables | Development (safe) |
| `create` | Drops + recreates schema each run | Development (quick) |
| `create-drop` | Drops on shutdown too | Testing |

**For production:** Never use `create` or `update`. Use **Flyway** for structured migrations (covered in Module 8).

---

## Useful JPA Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskforge
    username: taskforge
    password: taskforge_dev
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
  jpa:
    open-in-view: false           # IMPORTANT: disable for APIs (see note)
    show-sql: true                # log SQL (dev only)
    hibernate:
      ddl-auto: update            # dev-safe
    properties:
      hibernate:
        format_sql: true
        default_schema: public
        jdbc:
          batch_size: 20          # batch inserts for performance
        order_inserts: true
        order_updates: true
```

> **open-in-view: false** — By default, Spring opens a DB connection for the entire HTTP request. For REST APIs, disable this to close connections as soon as the transaction ends, reducing connection pressure. Spring Boot logs a warning if you don't set this explicitly.

---

## Docker PostgreSQL for Development

```bash
# Start PostgreSQL in Docker
docker run -d \
  --name taskforge-db \
  -e POSTGRES_DB=taskforge \
  -e POSTGRES_USER=taskforge \
  -e POSTGRES_PASSWORD=taskforge_dev \
  -p 5432:5432 \
  postgres:15-alpine

# Verify it's running
docker ps
docker exec -it taskforge-db psql -U taskforge -d taskforge -c '\dt'
```

Add to `application-dev.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskforge
    username: taskforge
    password: taskforge_dev
```

And to `application-test.yml` (use H2 for tests):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

## Try It Yourself

1. Start PostgreSQL with Docker (command above)
2. Add the `spring-boot-starter-data-jpa` and PostgreSQL driver to your pom.xml (already in the Module 2 pom.xml)
3. Create the `Task` entity class
4. Set `spring.jpa.hibernate.ddl-auto=create-drop`
5. Start the app and check the logs — Hibernate should print the `CREATE TABLE` SQL
6. Connect with a DB client (TablePlus, DBeaver, pgAdmin) to confirm the `tasks` table exists

---

## Capstone Connection

**TaskForge domain model (4 entities, all interconnected):**

```
User ──────────────────────────── owns ──── Project
 │                                              │
 │                                              ├── members (User many-to-many)
 │                                              └── tasks (Task one-to-many)
 │
 └── assigned tasks (Task many-to-one)
 └── created tasks (Task many-to-one)
 └── comments (Comment one-to-many)

Task ──── comments (Comment one-to-many)
```

We'll map this in [03 — Relationships](./03-relationships.md).

**Next:** [02 — Entity Lifecycle](./02-entity-lifecycle.md) — how Hibernate tracks entities and when SQL is actually executed.
