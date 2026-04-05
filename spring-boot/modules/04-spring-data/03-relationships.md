# 03 — Relationships

## Overview

TaskForge has 4 entities and 5 relationships:

```
User ←───────────────── owns ──────────────── Project
 │                                                │
 │◄── assigned_to ────── Task ─── belongs_to ───►│
 │◄── created_by ────────│
                         │
                       Comment ──── written_by ──►User
```

In JPA, relationships are defined with `@OneToMany`, `@ManyToOne`, and `@ManyToMany` annotations.

---

## @ManyToOne — The Common Side

The "many" side of a relationship (the FK column lives here).

**Task → Project (Many tasks belong to one project):**

```java
// In Task.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id", nullable = false)
private Project project;

// In Task.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "assignee_id")  // nullable — unassigned tasks
private User assignee;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by_id", nullable = false, updatable = false)
private User createdBy;
```

**Always use `FetchType.LAZY` for @ManyToOne.** Eager loading here means every time you load a Task, you also load its Project AND that Project's owner AND... you get the idea.

---

## @OneToMany — The Inverse Side

The "one" side of a relationship. The FK column is on the other table, so this side uses `mappedBy`.

**Project → List\<Task\>:**

```java
// In Project.java
@OneToMany(mappedBy = "project",           // refers to Task.project field
           cascade = CascadeType.ALL,       // delete project → delete tasks
           orphanRemoval = true,            // remove task from list → delete it
           fetch = FetchType.LAZY)          // ALWAYS lazy for collections
private List<Task> tasks = new ArrayList<>();
```

**User → List\<Task\> (created tasks):**

```java
// In User.java
@OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
private List<Task> createdTasks = new ArrayList<>();
```

> **Important:** Always initialize collection fields (`= new ArrayList<>()`) to avoid `NullPointerException` when the collection is empty.

---

## @ManyToMany — Project Members

A Project can have many Users as members, and a User can be a member of many Projects. This requires a **join table**.

```java
// In Project.java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "project_members",
    joinColumns = @JoinColumn(name = "project_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id")
)
private Set<User> members = new HashSet<>();  // Set avoids duplicates
```

```java
// In User.java — the inverse side (no @JoinTable here)
@ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
private Set<Project> memberProjects = new HashSet<>();
```

This creates the table:
```sql
CREATE TABLE project_members (
    project_id BIGINT REFERENCES projects(id),
    user_id    BIGINT REFERENCES users(id),
    PRIMARY KEY (project_id, user_id)
);
```

**Node.js TypeORM equiv:**
```typescript
@ManyToMany(() => User)
@JoinTable({ name: 'project_members' })
members: User[];
```

---

## The Complete Entity Set

### User Entity

```java
// src/main/java/com/taskforge/domain/User.java
package com.taskforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;   // bcrypt hash

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> createdTasks = new ArrayList<>();

    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Project> memberProjects = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Project Entity

```java
// src/main/java/com/taskforge/domain/Project.java
package com.taskforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "project",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_members",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper method to check membership
    public boolean isMember(Long userId) {
        return owner.getId().equals(userId) ||
               members.stream().anyMatch(m -> m.getId().equals(userId));
    }

    // Helper to add member without exposing collection directly
    public void addMember(User user) {
        members.add(user);
        user.getMemberProjects().add(this);
    }
}
```

### Task Entity

```java
// src/main/java/com/taskforge/domain/Task.java
package com.taskforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @OneToMany(mappedBy = "task",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Comment Entity

```java
// src/main/java/com/taskforge/domain/Comment.java
package com.taskforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

---

## FetchType: LAZY vs EAGER

| FetchType | Behavior | When to use |
|-----------|----------|-------------|
| `LAZY` | Association NOT loaded until accessed | **Always** for collections |
| `EAGER` | Association loaded immediately with parent | Almost never |

**EAGER is dangerous:**
- `FetchType.EAGER` on a `@OneToMany` = Cartesian product queries
- Hibernate may generate multiple queries or a massive JOIN
- You load data you don't need on every query

**Rule of thumb:** Always start with `LAZY`. If you need the association, use `JOIN FETCH` in your specific query.

---

## CascadeType

Controls what happens to related entities when you perform an operation on the parent:

| CascadeType | Effect |
|-------------|--------|
| `PERSIST` | Saving parent also saves children |
| `MERGE` | Merging parent also merges children |
| `REMOVE` | Deleting parent also deletes children |
| `REFRESH` | Refreshing parent also refreshes children |
| `DETACH` | Detaching parent also detaches children |
| `ALL` | All of the above |

```java
// Project.tasks with CascadeType.ALL + orphanRemoval:
project.getTasks().add(newTask);   // → INSERT into tasks (cascade PERSIST)
project.getTasks().remove(task);   // → DELETE from tasks (orphanRemoval)
projectRepository.delete(project); // → DELETE all tasks too (cascade REMOVE)
```

> **Caution:** `CascadeType.ALL` on ManyToMany is usually wrong — deleting one User shouldn't delete all their Projects!

---

## Database Schema (Generated SQL)

With `ddl-auto: create-drop`, Hibernate generates:

```sql
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE projects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    owner_id    BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE project_members (
    project_id BIGINT REFERENCES projects(id),
    user_id    BIGINT REFERENCES users(id),
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE tasks (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority      VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    project_id    BIGINT NOT NULL REFERENCES projects(id),
    assignee_id   BIGINT REFERENCES users(id),
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT NOT NULL,
    task_id    BIGINT NOT NULL REFERENCES tasks(id),
    author_id  BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP
);
```

---

## Try It Yourself

1. Create all 4 entity classes (User, Project, Task, Comment)
2. Create the 3 enum classes (Role, TaskStatus, Priority) if not done yet
3. Set `ddl-auto: create-drop` and start the app
4. Run `docker exec -it taskforge-db psql -U taskforge -d taskforge -c '\dt'`
5. You should see 5 tables: users, projects, tasks, comments, project_members
6. Inspect columns: `\d tasks` should show all FKs

---

## Capstone Connection

These 4 entities are the core of TaskForge. All repositories, services, and controllers work with these entities and their relationships. Notice that:
- `Project.isMember()` is a domain method — business logic in the entity
- All collections are initialized to prevent NPEs
- `updatable = false` on `created_by_id` — never allow changing task author after creation

**Next:** [04 — Transactions](./04-transactions.md) — @Transactional, propagation, and rollback behavior.
