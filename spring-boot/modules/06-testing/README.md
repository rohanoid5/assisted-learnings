# Module 6: Testing

## Overview

A Spring Boot app without tests is a ticking time bomb. In this module you'll learn the full Spring Boot testing toolkit — from fast unit tests that mock dependencies, to integration tests that spin up a real (test) application context, to slice tests that only load the parts you need.

If you've written Jest + Supertest tests, the patterns here will feel immediately familiar. Spring just has more built-in support for different test "slices" (controller-only, repository-only, full app), which leads to faster and more focused tests.

---

## Learning Objectives

- [ ] Write **unit tests** for your service layer using Mockito `@MockBean`
- [ ] Write **integration tests** for controllers using `MockMvc`
- [ ] Understand the difference between `@SpringBootTest` (full context) vs slice tests
- [ ] Use `@DataJpaTest` to test repositories against an in-memory H2 database
- [ ] Use `@WebMvcTest` to test controllers in isolation (no DB, no auth)
- [ ] Write tests that cover the **happy path**, **validation errors**, and **authorization failures**
- [ ] Understand test profiles and the H2 in-memory database setup

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-springboottest.md](01-springboottest.md) | Full application context tests — integration testing |
| 2 | [02-mockbean.md](02-mockbean.md) | Mocking dependencies with @MockBean (≈ jest.mock) |
| 3 | [03-mock-mvc.md](03-mock-mvc.md) | Testing HTTP endpoints without a running server |
| 4 | [04-jpa-test.md](04-jpa-test.md) | @DataJpaTest — testing repositories with H2 |

---

## Estimated Time

**3–5 hours** (including exercises)

---

## Prerequisites

- [Module 1–5](../01-introduction/) completed
- TaskForge secured with JWT (Module 5 capstone milestone)

---

## Capstone Milestone

By the end of this module TaskForge will have a **comprehensive test suite**:

- `ProjectServiceTest` — unit tests with `@MockBean` for the repository
- `TaskServiceTest` — unit tests covering status transition logic
- `ProjectControllerTest` — MockMvc integration tests (with/without auth)
- `AuthControllerTest` — register and login endpoint tests
- `TaskRepositoryTest` — `@DataJpaTest` for custom query methods
- All tests run against H2 in-memory database
- Test coverage: happy path, validation errors, auth failures, not-found cases

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
