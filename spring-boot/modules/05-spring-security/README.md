# Module 5: Spring Security

## Overview

TaskForge currently has zero authentication — anyone can call any endpoint and do anything. In this module you'll add **Spring Security** to lock it down: JWT-based authentication, role-based authorization, and an introduction to OAuth2.

If you've used Passport.js, the mental model is very similar: a chain of middleware (called *filters* in Spring) processes each request, extracts credentials, and either lets the request through or returns a 401/403.

---

## Learning Objectives

- [ ] Understand the **Spring Security filter chain** and how it mirrors Passport.js
- [ ] Configure **HTTP security** with `SecurityFilterChain`
- [ ] Implement **JWT authentication** from scratch (token generation, signing, validation)
- [ ] Add a **JWT filter** to the security chain
- [ ] Protect endpoints with **role-based authorization** using `@PreAuthorize`
- [ ] Understand **method-level security** vs URL-level security
- [ ] Configure **OAuth2 login** with GitHub (overview)
- [ ] Handle authentication **exceptions** (401 vs 403)

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-authentication.md](01-authentication.md) | UserDetailsService, PasswordEncoder, AuthenticationManager |
| 2 | [02-authorization.md](02-authorization.md) | Roles, authorities, @PreAuthorize, URL-level security |
| 3 | [03-oauth2.md](03-oauth2.md) | OAuth2 login flow, Spring Security OAuth2 client |
| 4 | [04-jwt-authentication.md](04-jwt-authentication.md) | Building a complete JWT auth system end-to-end |

---

## Estimated Time

**4–6 hours** (including exercises)

---

## Prerequisites

- [Module 1–4](../01-introduction/) completed
- TaskForge connected to PostgreSQL (Module 4 capstone milestone)

---

## Capstone Milestone

By the end of this module TaskForge will be **fully secured**:

- `POST /api/auth/register` — creates user with hashed password (BCrypt)
- `POST /api/auth/login` — returns JWT token
- All other endpoints require a valid JWT in the `Authorization: Bearer <token>` header
- **ADMIN** can manage all projects and users
- **MANAGER** can create projects and manage tasks in their projects
- **USER** can only manage their own tasks and add comments
- `@PreAuthorize` guards on service methods for fine-grained access control

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
