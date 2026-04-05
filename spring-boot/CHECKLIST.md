# Spring Boot Knowledge Checklist

Use this file to periodically self-assess. Review it monthly and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Introduction

### 1.1 Terminology
- [ ] Can explain the difference between Spring Framework and Spring Boot
- [ ] Can name at least 4 core Spring Framework modules (Web, Data, Security, AOP, etc.)
- [ ] Understands what "opinionated defaults" and "convention over configuration" mean

### 1.2 Architecture
- [ ] Can describe the layered architecture: Controller → Service → Repository
- [ ] Knows the role of the DispatcherServlet in the request lifecycle
- [ ] Can explain how Spring Boot wraps and extends the Spring Framework

### 1.3 Why Spring?
- [ ] Can articulate the core problems Spring solves (boilerplate, tight coupling, testability)
- [ ] Can compare Spring Boot to a bare-metal Java EE / Jakarta EE approach

### 1.4 Configuration
- [ ] Understands `application.properties` vs `application.yml` trade-offs
- [ ] Knows how to activate and use profiles (`@Profile`, `spring.profiles.active`)
- [ ] Can bind external config to a typed class with `@ConfigurationProperties`
- [ ] Understands the property resolution order (env vars > command-line > YAML > defaults)

### 1.5 Dependency Injection
- [ ] Can explain constructor injection vs field injection vs setter injection
- [ ] Knows why constructor injection is preferred (immutability, testability)
- [ ] Can resolve bean ambiguity with `@Primary` and `@Qualifier`

### 1.6 Spring IoC Container
- [ ] Can explain what an IoC container is and how it manages bean lifecycle
- [ ] Understands the difference between `BeanFactory` and `ApplicationContext`
- [ ] Knows the lifecycle hooks: `@PostConstruct`, `@PreDestroy`, `InitializingBean`

### 1.7 Spring AOP
- [ ] Can explain Aspect-Oriented Programming and name 3 practical use cases (logging, security, transactions)
- [ ] Understands Join Point, Pointcut, Advice, Aspect, and Weaving
- [ ] Can write a simple `@Aspect` class with `@Before` or `@Around` advice
- [ ] Knows the proxy limitation: AOP only intercepts external calls (self-invocation doesn't work)

### 1.8 Annotations
- [ ] Knows the distinction between `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`
- [ ] Understands `@Autowired`, `@Value`, `@Bean`, `@Configuration`
- [ ] Can explain why `@Repository` adds persistence exception translation

### 1.9 Spring Bean Scope
- [ ] Can list and explain all 5 bean scopes: singleton, prototype, request, session, application
- [ ] Knows that singleton is the default
- [ ] Understands the issue of injecting a prototype bean into a singleton (use `ObjectProvider` or `@Lookup`)

---

## Module 2 — Spring Boot Core

### 2.1 Spring Boot Starters
- [ ] Understands what a starter is and how it bundles dependencies + autoconfiguration
- [ ] Knows the most common starters: `web`, `data-jpa`, `security`, `test`, `actuator`
- [ ] Can create and publish a custom starter

### 2.2 Autoconfiguration
- [ ] Can explain how autoconfiguration works (condition classes, `AutoConfiguration.imports`)
- [ ] Understands `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`
- [ ] Knows how to disable or override specific autoconfiguration
- [ ] Can use `--debug` flag to see autoconfiguration report

### 2.3 Embedded Server
- [ ] Understands how Spring Boot embeds Tomcat/Jetty/Undertow
- [ ] Can configure server port, context path, connection timeout, and SSL
- [ ] Can explain the trade-off vs deploying a WAR to an external container

### 2.4 Spring Boot Actuator
- [ ] Knows the built-in endpoints: `/health`, `/info`, `/metrics`, `/env`, `/beans`
- [ ] Can enable, disable, and secure individual actuator endpoints
- [ ] Can create a custom `HealthIndicator`
- [ ] Understands how to integrate metrics with Prometheus via `micrometer-registry-prometheus`

---

## Module 3 — Spring MVC

### 3.1 Servlet Basics
- [ ] Understands the Servlet lifecycle: `init()` → `service()` → `destroy()`
- [ ] Knows what a Servlet container (Tomcat) does
- [ ] Can explain how Spring MVC builds on top of the Servlet API

### 3.2 Spring MVC Architecture
- [ ] Can trace the full request lifecycle: Client → DispatcherServlet → HandlerMapping → Controller → ViewResolver
- [ ] Knows when and how to use `HandlerInterceptor`
- [ ] Understands `@ControllerAdvice` as a cross-cutting concern for all controllers

### 3.3 Spring MVC Components
- [ ] Knows `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
- [ ] Understands `@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader`
- [ ] Can configure `HttpMessageConverter`s and understands content negotiation

### 3.4 REST Controllers
- [ ] Can build a complete CRUD REST controller
- [ ] Uses `ResponseEntity<T>` to control status codes, headers, and body
- [ ] Handles exceptions globally with `@ControllerAdvice` + `@ExceptionHandler`
- [ ] Validates request bodies with `@Valid` / `@Validated` and handles `MethodArgumentNotValidException`

---

## Module 4 — Spring Data & Hibernate

### 4.1 Hibernate Basics
- [ ] Can explain ORM and why it reduces boilerplate
- [ ] Knows the common mapping annotations: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`
- [ ] Understands lazy vs eager loading and when each is appropriate

### 4.2 Entity Lifecycle
- [ ] Can name the 4 entity states: transient, persistent (managed), detached, removed
- [ ] Understands when Hibernate flushes to the DB (end of transaction, explicit flush, query before flush)
- [ ] Knows the N+1 problem and can fix it with `JOIN FETCH` or `@EntityGraph`

### 4.3 Relationships
- [ ] Can correctly map `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`
- [ ] Understands owning side vs inverse side (`mappedBy`)
- [ ] Knows `@JoinColumn`, `@JoinTable`, cascading types, and `orphanRemoval`

### 4.4 Transactions
- [ ] Understands `@Transactional` propagation levels (REQUIRED, REQUIRES_NEW, NESTED, etc.)
- [ ] Knows the 4 isolation levels and what anomalies each prevents
- [ ] Can explain why `@Transactional` on `private` methods is silently ignored (proxy limitation)
- [ ] Knows `@Transactional(readOnly = true)` and its performance implications

### 4.5 Spring Data JPA
- [ ] Can create repository interfaces extending `CrudRepository`, `JpaRepository`, `PagingAndSortingRepository`
- [ ] Knows derived query method naming conventions (e.g., `findByEmailAndActiveTrue`)
- [ ] Can write JPQL and native SQL queries with `@Query`
- [ ] Uses `Pageable` and `Sort` for pagination and sorting

### 4.6 Spring Data MongoDB
- [ ] Can configure `MongoRepository` and basic CRUD operations
- [ ] Knows `@Document`, `@Field`, `@Id` annotations
- [ ] Understands the main differences from JPA (no lazy loading, document model)

### 4.7 Spring Data JDBC
- [ ] Understands how Spring Data JDBC differs from Spring Data JPA (no Session, no lazy loading, no proxies)
- [ ] Can use `CrudRepository` with Spring Data JDBC
- [ ] Knows when JDBC is a better fit than JPA (simpler domain models, explicit control)

---

## Module 5 — Spring Security

### 5.1 Authentication
- [ ] Understands the Spring Security filter chain and where authentication happens
- [ ] Can implement `UserDetailsService` to load users from a database
- [ ] Knows how to use `PasswordEncoder` (BCrypt) — never stores passwords in plaintext
- [ ] Understands `SecurityContextHolder` and how authentication propagates per-request

### 5.2 Authorization
- [ ] Can configure URL-based access rules in `HttpSecurity`
- [ ] Knows method-level security with `@PreAuthorize("hasRole('ADMIN')")` and `@PostAuthorize`
- [ ] Understands the difference between a role and an authority (GrantedAuthority)

### 5.3 OAuth2
- [ ] Can explain the OAuth2 Authorization Code flow step by step
- [ ] Can configure Spring Security as an OAuth2 client (Google, GitHub, custom)
- [ ] Understands the difference between OAuth2 (authorization) and OpenID Connect (authentication)

### 5.4 JWT Authentication
- [ ] Understands JWT structure: header.payload.signature
- [ ] Can implement a custom `OncePerRequestFilter` to validate JWTs
- [ ] Knows stateless session management (`SessionCreationPolicy.STATELESS`) and its trade-offs
- [ ] Understands token expiry, refresh token patterns, and token revocation challenges

---

## Module 6 — Testing

### 6.1 @SpringBootTest
- [ ] Understands the difference between `@SpringBootTest` (full context) and sliced tests
- [ ] Knows `webEnvironment` options: `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, `NONE`
- [ ] Can use `@TestPropertySource` or `@DynamicPropertySource` to override properties

### 6.2 @MockBean & Mockito
- [ ] Can create mocks with `@MockBean` and stub with `when(mock.method()).thenReturn(value)`
- [ ] Understands `@Mock` (plain Mockito, no Spring context) vs `@MockBean` (replaces Spring bean)
- [ ] Can verify interactions and call counts with `verify(mock, times(n)).method()`
- [ ] Uses `ArgumentCaptor` to inspect arguments passed to a mock

### 6.3 MockMvc
- [ ] Can write controller tests using `MockMvc` without a running server
- [ ] Knows `perform()`, `andExpect(status().isOk())`, `andDo(print())`
- [ ] Can assert response body fields with `jsonPath("$.field", is("value"))`

### 6.4 @DataJpaTest
- [ ] Understands that `@DataJpaTest` only loads JPA-related components (repository + DB)
- [ ] Can write repository tests with an in-memory H2 database
- [ ] Uses `@Sql` to set up test data, `@Rollback` to control transaction rollback

---

## Module 7 — Microservices & Spring Cloud

### 7.1 Microservices Introduction
- [ ] Can list 3 key benefits and 3 key challenges of microservices over a monolith
- [ ] Understands bounded contexts and the role of domain-driven design
- [ ] Names the main patterns: API gateway, service discovery, circuit breaker, distributed tracing

### 7.2 Spring Cloud Gateway
- [ ] Can configure route predicates (path, host, method) and filters (add/remove headers, rewrite path)
- [ ] Understands global filters vs route-level filters
- [ ] Can configure rate limiting and retry policies on routes

### 7.3 Spring Cloud Config
- [ ] Can set up a Config Server backed by a Git repository
- [ ] Knows how clients resolve configuration (`bootstrap.yml` / Config First bootstrap)
- [ ] Understands live config refresh: `@RefreshScope` + `/actuator/refresh` + Spring Cloud Bus

### 7.4 Eureka Service Discovery
- [ ] Can stand up a Eureka Server and register client applications
- [ ] Understands heartbeat mechanism, registration lease, and self-preservation mode
- [ ] Can do client-side load balancing via `@LoadBalanced` `RestTemplate` or `WebClient`

### 7.5 OpenFeign
- [ ] Can declare a type-safe Feign client with `@FeignClient(name = "service-name")`
- [ ] Knows how to configure timeouts, custom error decoders, and request interceptors
- [ ] Understands integration with Eureka for service-name resolution

### 7.6 Circuit Breaker (Resilience4j)
- [ ] Can configure `@CircuitBreaker`, `@Retry`, `@TimeLimiter` with Resilience4j
- [ ] Understands the CLOSED → OPEN → HALF-OPEN state machine and transition thresholds
- [ ] Can implement and test a fallback method

### 7.7 Micrometer & Distributed Tracing
- [ ] Understands Micrometer as a vendor-neutral metrics facade
- [ ] Can add custom `Counter`, `Gauge`, and `Timer` metrics
- [ ] Knows how to set up distributed tracing with Micrometer Tracing + Zipkin or Jaeger
- [ ] Can correlate logs and traces using trace ID / span ID in MDC

---

## Module 8 — Capstone (TaskForge)

- [ ] Can describe the TaskForge architecture: layers, security model, data model
- [ ] Has built and run the application end-to-end locally
- [ ] Can add a new resource (entity → repository → service → controller) independently
- [ ] Has written an integration test covering a non-trivial business flow
- [ ] Has implemented and verified at least one Spring Security rule

---

## Review Log

Use this table to track monthly self-assessments.

| Date | Topics Reviewed | Gaps Identified |
|------|----------------|-----------------|
| | | |
| | | |
| | | |
