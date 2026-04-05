# Module 1: Introduction to Spring & Spring Boot

## Overview

Before you write a single line of Spring Boot code, you need to understand the **mental models** that underpin the entire framework. This module is your conceptual foundation — everything from Module 2 onwards assumes you understand these ideas.

If you're coming from NestJS, many of these concepts (DI, IoC, decorators-as-annotations) will feel very familiar. If you're purely from Express-land, expect a slight paradigm shift — Spring Boot is *opinionated* and *convention-driven* in ways that Express is not.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain the difference between **Spring Framework** and **Spring Boot**
- [ ] Describe the overall **Spring Boot architecture** and layered application structure
- [ ] Articulate **why you'd choose Spring Boot** over competing frameworks
- [ ] Configure a Spring Boot app using **`application.yml`** and multiple profiles
- [ ] Understand and write **Dependency Injection** the Spring way
- [ ] Explain what the **IoC Container** is and how it manages beans
- [ ] Describe **AOP** and when to use it (and draw the parallel to Express middleware)
- [ ] Identify and use the most important Spring **annotations**
- [ ] Understand **Bean Scopes** and when to use non-singleton scopes

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-terminology.md](01-terminology.md) | Spring vs Spring Boot, key vocabulary |
| 2 | [02-architecture.md](02-architecture.md) | Framework modules, layered architecture |
| 3 | [03-why-spring.md](03-why-spring.md) | When to choose Spring Boot and why it dominates enterprise Java |
| 4 | [04-configuration.md](04-configuration.md) | `application.yml`, profiles, `@Value`, `@ConfigurationProperties` |
| 5 | [05-dependency-injection.md](05-dependency-injection.md) | Constructor, field, and setter injection |
| 6 | [06-spring-ioc.md](06-spring-ioc.md) | The IoC container, ApplicationContext, component scanning |
| 7 | [07-spring-aop.md](07-spring-aop.md) | Aspects, Advice, Pointcuts — cross-cutting concerns |
| 8 | [08-annotations.md](08-annotations.md) | Core annotation reference |
| 9 | [09-spring-bean-scope.md](09-spring-bean-scope.md) | Singleton, Prototype, Request, Session scopes |

---

## Estimated Time

**4–6 hours** (including exercises)

---

## Prerequisites

- Basic Java knowledge: classes, interfaces, generics, lambdas
- No prior Spring knowledge required

---

## Capstone Milestone

By the end of this module you will **bootstrap the TaskForge project**:

- Generate the project using Spring Initializr
- Set up `application.yml` with `dev` and `prod` profiles
- Create your first `@Service` bean with constructor injection
- Add an AOP logging aspect that logs all service method calls

See [exercises/README.md](exercises/README.md) for the step-by-step capstone tasks.
