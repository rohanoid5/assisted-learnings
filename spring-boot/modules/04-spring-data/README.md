# Module 4: Spring Data & Hibernate

## Overview

So far TaskForge stores everything in memory — none of it survives a restart. In this module you'll connect it to a real database using **Spring Data JPA** and **Hibernate**, Spring Boot's de-facto persistence stack.

If you've used TypeORM or Prisma, the concepts here are very close. Hibernate is the ORM (like TypeORM), JPA is the specification it implements (like the TypeORM interface), and Spring Data is the layer on top that gives you repository abstractions (like Prisma's generated client).

---

## Learning Objectives

- [ ] Understand **Hibernate** as an ORM and what JPA is
- [ ] Map Java classes to database tables using `@Entity`, `@Table`, `@Column`
- [ ] Understand the **Entity Lifecycle** (Transient → Managed → Detached → Removed)
- [ ] Model **relationships**: `@OneToMany`, `@ManyToOne`, `@ManyToMany`
- [ ] Write **custom queries** using JPQL and `@Query`
- [ ] Manage **transactions** with `@Transactional`
- [ ] Use **Spring Data JPA repositories** — and understand what they generate for you
- [ ] Implement **pagination and sorting** with `Pageable`
- [ ] Know when to use **Spring Data MongoDB** and **Spring Data JDBC** (overview)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-hibernate-basics.md](01-hibernate-basics.md) | ORM concepts, Hibernate vs JPA, SessionFactory vs EntityManager |
| 2 | [02-entity-lifecycle.md](02-entity-lifecycle.md) | The 4 entity states and how Hibernate tracks changes |
| 3 | [03-relationships.md](03-relationships.md) | @OneToMany, @ManyToOne, @ManyToMany with real TaskForge examples |
| 4 | [04-transactions.md](04-transactions.md) | @Transactional, transaction propagation, rollback rules |
| 5 | [05-spring-data-jpa.md](05-spring-data-jpa.md) | JpaRepository, derived queries, @Query, Pageable |
| 6 | [06-spring-data-mongodb.md](06-spring-data-mongodb.md) | MongoRepository, document mapping (awareness overview) |
| 7 | [07-spring-data-jdbc.md](07-spring-data-jdbc.md) | Spring Data JDBC — lighter alternative to JPA |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- [Module 1 — Introduction](../01-introduction/) completed
- [Module 2 — Spring Boot Core](../02-spring-boot-core/) completed
- [Module 3 — Spring MVC](../03-spring-mvc/) completed
- PostgreSQL running (use the Docker command from the root README)

---

## Capstone Milestone

By the end of this module TaskForge will have **full database persistence**:

- All 4 entities (`User`, `Project`, `Task`, `Comment`) mapped to PostgreSQL tables
- Entity relationships configured (OneToMany, ManyToMany)
- Custom repository methods for complex queries (tasks by status, tasks by assignee, etc.)
- Pagination on the `GET /api/tasks` and `GET /api/projects` endpoints
- H2 in-memory database configured for the test profile

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
