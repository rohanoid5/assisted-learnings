# 1.7 — Spring AOP (Aspect-Oriented Programming)

## Concept

**AOP** is a programming paradigm for handling **cross-cutting concerns** — functionality that cuts across many classes (logging, security checks, performance monitoring, transaction management) and doesn't belong inside any single class.

**Express middleware analogy:** AOP is Spring's equivalent of Express middleware — code that runs before/after a function without that function knowing about it.

```javascript
// Express middleware — runs before route handlers
app.use((req, res, next) => {
    console.log(`${req.method} ${req.url} called at ${new Date()}`);
    next();
});
```

```java
// Spring AOP — runs before/after service methods
@Aspect
@Component
public class LoggingAspect {

    @Before("execution(* com.taskforge.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("Calling: " + joinPoint.getSignature().getName());
    }
}
```

The service method being called has **no idea** the logging aspect exists. It's completely transparent.

---

## Key AOP Terminology

| Term | Definition | Analogy |
|------|-----------|---------|
| **Aspect** | The class containing cross-cutting logic | An Express middleware module |
| **Advice** | The code to run (`@Before`, `@After`, `@Around`) | The middleware function body |
| **Pointcut** | The expression defining *which* methods to intercept | The path pattern in `app.use('/api', ...)` |
| **JoinPoint** | A specific method invocation being intercepted | The `req` object in a middleware |
| **Weaving** | The process of applying aspects to target objects | Mounting middleware into the app |
| **Target** | The object whose method is being intercepted | The route handler |

---

## Types of Advice

### `@Before` — Runs before the method

```java
@Before("execution(* com.taskforge.service.*.*(..))")
public void logMethodCall(JoinPoint joinPoint) {
    String methodName = joinPoint.getSignature().getName();
    Object[] args = joinPoint.getArgs();
    log.info("Calling {} with args: {}", methodName, Arrays.toString(args));
}
```

### `@After` — Runs after the method (regardless of success/failure)

```java
@After("execution(* com.taskforge.service.*.*(..))")
public void logMethodComplete(JoinPoint joinPoint) {
    log.info("Completed: {}", joinPoint.getSignature().getName());
}
```

### `@AfterReturning` — Runs only on success, can see the return value

```java
@AfterReturning(pointcut = "execution(* com.taskforge.service.TaskService.createTask(..))",
                returning = "result")
public void logTaskCreated(JoinPoint joinPoint, Object result) {
    log.info("Task created: {}", result);
}
```

### `@AfterThrowing` — Runs only on exception

```java
@AfterThrowing(pointcut = "execution(* com.taskforge.service.*.*(..))",
               throwing = "ex")
public void logException(JoinPoint joinPoint, Exception ex) {
    log.error("Exception in {}: {}", joinPoint.getSignature().getName(), ex.getMessage());
}
```

### `@Around` — Most powerful: wraps the entire method (like a full middleware)

```java
@Around("execution(* com.taskforge.service.*.*(..))")
public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.currentTimeMillis();
    
    try {
        Object result = joinPoint.proceed();   // ← actually calls the method
        long elapsed = System.currentTimeMillis() - start;
        log.info("{} took {}ms", joinPoint.getSignature().getName(), elapsed);
        return result;
    } catch (Exception ex) {
        log.error("Exception in {}: {}", joinPoint.getSignature().getName(), ex.getMessage());
        throw ex;
    }
}
```

---

## Pointcut Expressions

The most common syntax: `execution(modifiers? return-type declaring-type? method-name(params) throws?)`

```java
// All methods in any class in the service package
"execution(* com.taskforge.service.*.*(..))"

// Only public methods returning void
"execution(public void com.taskforge.service.*.*(..))"

// Any method named "createTask"
"execution(* *.createTask(..))"

// Any method in classes annotated with @Service
"@within(org.springframework.stereotype.Service)"

// Any method annotated with @Transactional
"@annotation(org.springframework.transaction.annotation.Transactional)"
```

**Reusable pointcuts:**

```java
@Aspect
@Component
public class LoggingAspect {

    // Define once, reference multiple times
    @Pointcut("execution(* com.taskforge.service.*.*(..))")
    public void serviceLayer() {}

    @Before("serviceLayer()")
    public void logBefore(JoinPoint joinPoint) { ... }

    @After("serviceLayer()")
    public void logAfter(JoinPoint joinPoint) { ... }
}
```

---

## Complete Example: Logging Aspect for TaskForge

```java
package com.taskforge.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Pointcut("execution(* com.taskforge.service.*.*(..))")
    public void serviceLayer() {}

    @Around("serviceLayer()")
    public Object logAndTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        log.debug("→ Entering {}", method);
        
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.debug("← Exiting {} ({}ms)", method, elapsed);
            return result;
        } catch (Exception ex) {
            log.error("✗ Exception in {}: {}", method, ex.getMessage());
            throw ex;
        }
    }
}
```

For this to work, add `spring-boot-starter-aop` to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## Spring Uses AOP Internally

Many Spring features you'll use are implemented with AOP under the hood:

| Spring Feature | AOP Usage |
|---------------|----------|
| `@Transactional` | Around advice that begins/commits/rolls back transactions |
| `@Cacheable` | Around advice that checks/sets cache before calling method |
| `@PreAuthorize` | Before advice that checks security before method runs |
| `@Async` | Around advice that runs method in a thread pool |
| `@Scheduled` | Not AOP, but similar proxy-based magic |

When you write `@Transactional` on a service method, Spring automatically wraps it with an `@Around` aspect that manages the database transaction. You never write the `beginTransaction()` / `commit()` / `rollback()` code yourself.

---

## How Spring AOP Works (Proxies)

Spring AOP works by creating **proxy objects** — thin wrapper classes that intercept method calls:

```
Your @Service class ← Spring creates a proxy of it
       │
       ├── When someone calls service.createTask(...)
       │
       ▼
    [Proxy]
       ├── Runs @Before advice
       ├── Calls actual service.createTask(...)
       └── Runs @After advice
```

**Important limitation:** Spring AOP only works on **Spring-managed beans** and only intercepts calls **from outside the bean**. If a method calls another method in the *same class*, the proxy is bypassed:

```java
@Service
public class TaskService {

    @Transactional              // ← This WON'T trigger for self-calls
    public void doSomethingTransactional() { ... }

    public void publicMethod() {
        doSomethingTransactional();   // ← Self-call bypasses the proxy!
        // Fix: inject self, or restructure to avoid self-calls
    }
}
```

---

## Try It Yourself

**Exercise:** Add the `ServiceLoggingAspect` to your TaskForge project.

1. Add `spring-boot-starter-aop` to `pom.xml`
2. Create `src/main/java/com/taskforge/aspect/ServiceLoggingAspect.java` with the code above
3. Set `logging.level.com.taskforge: DEBUG` in `application-dev.yml`
4. Call `GET /api/greet` and observe the logs

You should see:
```
DEBUG → Entering GreetingService.greet(..)
DEBUG ← Exiting GreetingService.greet(..) (0ms)
```

<details>
<summary>Troubleshooting</summary>

- If you don't see the logs, check that `logging.level.com.taskforge.aspect: DEBUG` is set in `application-dev.yml`
- If you get `NoAspectFoundException`, make sure `spring-boot-starter-aop` is in your `pom.xml`
- Make sure your `@Aspect` class is also annotated with `@Component` (Spring won't find it otherwise)
</details>

---

## Capstone Connection

TaskForge uses two AOP aspects:
1. `ServiceLoggingAspect` — logs method entry/exit/timing for all service methods (just added)
2. Spring Security's internal aspects — `@PreAuthorize` checks on service methods (added in Module 5)

You don't write the security aspect yourself — Spring Security provides it. But understanding AOP is what makes `@PreAuthorize` seem magical (it's not — it's just an `@Around` advice).
