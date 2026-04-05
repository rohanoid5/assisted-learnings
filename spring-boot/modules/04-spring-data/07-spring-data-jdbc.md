# 07 — Spring Data JDBC

## Overview

Spring Data JDBC is a lighter-weight alternative to JPA/Hibernate that gives you the repository abstraction without the full ORM complexity.

**The philosophy:** No lazy loading, no dirty checking, no entity lifecycle, no caching. Just aggregates mapped to SQL. Simpler mental model, more explicit control.

---

## JPA vs Spring Data JDBC vs JDBC Template

| Feature | Spring Data JDBC | Spring Data JPA (Hibernate) | Plain JDBC / JdbcTemplate |
|---------|-------------------|-------------------------------|--------------------------|
| Repository abstraction | ✅ | ✅ | ❌ |
| Auto-generated SQL | ✅ basic | ✅ complex | ❌ |
| Derived query methods | Limited | ✅ full | ❌ |
| Lazy loading | ❌ (no lazy) | ✅ (LAZY/EAGER) | N/A |
| Dirty checking | ❌ (explicit save) | ✅ (auto) | N/A |
| Caching | ❌ | ✅ (L1/L2) | N/A |
| Entity lifecycle | ❌ | ✅ (Transient/Managed/Detached/Removed) | N/A |
| Learning curve | Low | Medium-High | Low |
| Control over SQL | Medium | Medium (opinionated) | Full |

**When to use Spring Data JDBC:**
- Simple CRUD with straightforward queries
- You want predictable SQL (no Hibernate "magic")
- Microservice with a small, focused domain
- You've been burned by Hibernate behavior before

**When to stick with JPA:**
- Complex relationships and inheritance hierarchies
- Large existing codebases using JPA
- Need advanced features (2nd-level cache, optimistic locking, generated queries for complex joins)

---

## Setup

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

No `spring-boot-starter-data-jpa` — these are separate.

---

## Aggregate Root + @Table

Spring Data JDBC models data as **aggregates** (from Domain-Driven Design). An aggregate root is the entry point — all access goes through it.

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;

@Table("products")
public class Product {

    @Id
    private Long id;          // must be Long or Integer (auto-assigned by JDBC)

    private String name;

    @Column("category_name")  // explicit column mapping
    private String categoryName;

    private double price;

    // Embedded collection: Product owns its Variants
    // Variants are stored in a "product_variants" table with FK "product"
    @MappedCollection(idColumn = "product_id")
    private Set<ProductVariant> variants = new HashSet<>();
}

@Table("product_variants")
public class ProductVariant {
    @Id private Long id;
    private String size;
    private String color;
    private int stock;
    // Note: no back-reference to Product — JDBC is one-directional
}
```

**Key differences from JPA entities:**
- No `@Entity` — just `@Table`
- No `@GeneratedValue` — Spring Data JDBC detects that `id` is null and does INSERT
- No `@OneToMany` / `@ManyToOne` annotations — use `@MappedCollection`
- Child entities (Variants) have no reference back to their parent
- An entity id of `null` means "new" → INSERT; non-null means "existing" → UPDATE

---

## CrudRepository

```java
public interface ProductRepository extends CrudRepository<Product, Long> {
    // Same derived query methods as JPA
    List<Product> findByCategoryName(String name);
    Optional<Product> findByName(String name);
    boolean existsByName(String name);
}
```

Or extend `ListCrudRepository` to get `List<T>` return types (no `Iterable`):

```java
public interface ProductRepository extends ListCrudRepository<Product, Long> { ... }
```

---

## @Query for Custom SQL

Spring Data JDBC uses **plain SQL** for custom queries (not JPQL):

```java
@Query("SELECT * FROM products WHERE price < :maxPrice AND category_name = :category")
List<Product> findByPriceAndCategory(@Param("maxPrice") double price, @Param("category") String cat);

@Query("SELECT COUNT(*) FROM products WHERE category_name = :category")
long countByCategory(@Param("category") String category);

// Modifying query
@Modifying
@Query("UPDATE products SET price = price * :multiplier WHERE category_name = :category")
void adjustPricesByCategory(@Param("multiplier") double multiplier, @Param("category") String category);
```

---

## No Dirty Checking — Explicit save()

The biggest mental shift from JPA:

```java
// JPA: dirty checking auto-saves
@Transactional
public void updateJpa(Long id, String newName) {
    Product p = repo.findById(id).orElseThrow();
    p.setName(newName);
    // automatically saved at commit
}

// Spring Data JDBC: must call save() explicitly
@Transactional
public void updateJdbc(Long id, String newName) {
    Product p = repo.findById(id).orElseThrow();
    p.setName(newName);
    repo.save(p);  // ← required! No dirty checking
}
```

**This is more explicit and predictable.** You always know when a SQL statement is sent.

---

## JdbcTemplate — Maximum Control

When you need raw SQL with full control, `JdbcTemplate` is the lowest-level Spring abstraction:

```java
@Repository
@RequiredArgsConstructor
public class TaskStatsRepository {

    private final JdbcTemplate jdbcTemplate;

    // Simple value
    public long countByProject(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tasks WHERE project_id = ?",
            Long.class,
            projectId
        );
    }

    // Map single row to object
    public TaskStats getStats(Long projectId) {
        return jdbcTemplate.queryForObject(
            "SELECT status, COUNT(*) as cnt FROM tasks " +
            "WHERE project_id = ? GROUP BY status",
            (rs, rowNum) -> new TaskStats(
                rs.getString("status"),
                rs.getLong("cnt")
            ),
            projectId
        );
    }

    // Multiple rows
    public List<TaskStats> getAllStats(Long projectId) {
        return jdbcTemplate.query(
            "SELECT status, COUNT(*) as cnt FROM tasks WHERE project_id = ? GROUP BY status",
            (rs, rowNum) -> new TaskStats(rs.getString("status"), rs.getLong("cnt")),
            projectId
        );
    }

    // Insert and get generated key
    @Transactional
    public Long insertAndGetId(String title, Long projectId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO tasks(title, project_id, status) VALUES(?, ?, 'TODO')",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            ps.setLong(2, projectId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
```

---

## NamedParameterJdbcTemplate

More readable than `?` placeholders:

```java
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final NamedParameterJdbcTemplate namedJdbc;

    public List<ProjectReport> getProjectReport(Long ownerId, LocalDate since) {
        String sql = """
            SELECT p.id, p.name, COUNT(t.id) as task_count,
                   COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) as done_count
            FROM projects p
            LEFT JOIN tasks t ON t.project_id = p.id AND t.created_at >= :since
            WHERE p.owner_id = :ownerId
            GROUP BY p.id, p.name
            """;

        Map<String, Object> params = Map.of("ownerId", ownerId, "since", since);

        return namedJdbc.query(sql, params, (rs, rowNum) ->
            new ProjectReport(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("task_count"),
                rs.getLong("done_count")
            )
        );
    }
}
```

---

## When to Use Each

```
New project, complex domain with relationships → Spring Data JPA
Simple domain, prefer explicit SQL → Spring Data JDBC
Need raw SQL flexibility, or reporting queries → JdbcTemplate / NamedParameterJdbcTemplate
Working with existing DB schema → JdbcTemplate (full control)
```

---

## Capstone Connection

TaskForge uses **Spring Data JPA** for the main domain. However, `JdbcTemplate` would be appropriate for:
- Dashboard statistics queries (complex GROUP BY aggregations)
- Bulk operations (update all tasks in a project)
- Reporting endpoints

You can mix both in the same Spring Boot app — JPA repositories for CRUD, JdbcTemplate for complex read queries.

**Next:** [Module 4 Exercises](./exercises/README.md) — build TaskForge's full persistence layer.
