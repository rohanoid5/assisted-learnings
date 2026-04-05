# 01 — Servlet Basics

## Concept

Before Spring MVC, to handle HTTP in Java you wrote **Servlets** — classes that implement `javax.servlet.Servlet` (now `jakarta.servlet.Servlet` in Jakarta EE 10).

A Servlet is basically: "here is a class; call `doGet()` when a GET request comes in, call `doPost()` for POST, etc."

**Node.js mental model:**
```
// Node.js (Express)
app.get('/tasks',  (req, res) => res.json(tasks));
app.post('/tasks', (req, res) => { ... });

// Old-school Java Servlet (what Spring wraps)
public class TaskServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ... {
        res.getWriter().write("[{...}]"); // manually write JSON!
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ... { ... }
}
```

Spring MVC hides all of this. You never write `HttpServlet` directly.

---

## How the Servlet Container Works

A **Servlet Container** (like Apache Tomcat) is the runtime that:

1. Listens on a TCP port (default 8080)
2. Parses incoming HTTP bytes into an `HttpServletRequest` object
3. Finds which Servlet should handle the request (via URL mapping)
4. Calls that Servlet's `service()` → `doGet()` / `doPost()` / etc.
5. Takes the `HttpServletResponse` the Servlet filled in and sends bytes back to the client

```
Client → TCP → Tomcat → HttpServletRequest → Servlet → HttpServletResponse → bytes → Client
```

**Node.js equiv:** Node's `http.createServer((req, res) => ...)` — Node itself is both the TCP listener and the "container".

In Spring Boot, Tomcat is embedded in the JAR. When your app starts, Spring Boot bootstraps Tomcat programmatically and registers exactly one Servlet: **DispatcherServlet**.

---

## The Servlet Lifecycle

Every Servlet goes through three lifecycle methods:

```java
// Called once when the Servlet is first loaded
public void init(ServletConfig config) throws ServletException { ... }

// Called for every HTTP request
public void service(HttpServletRequest req, HttpServletResponse res) throws ... { ... }

// Called when the container shuts down
public void destroy() { ... }
```

Spring's `DispatcherServlet` is a Servlet — it implements this exact interface. Its `service()` method is the entry point for every HTTP request your Spring Boot app receives.

---

## What Is DispatcherServlet?

DispatcherServlet is Spring MVC's **Front Controller**.

The pattern: instead of mapping every URL to a different Servlet (messy), map ALL URLs to ONE Servlet, and let that Servlet route to the right handler.

```
All requests → DispatcherServlet → finds handler → calls handler → sends response
```

**Node.js equiv:** Express's `app` itself — all requests go through it, and `app.use()` / `app.get()` defines the routing.

Spring Boot auto-registers DispatcherServlet for `/*` (all paths) via autoconfiguration. You'll never instantiate it yourself.

---

## The Servlet Request/Response Lifecycle (Simplified)

```
1. Client sends HTTP request
          ↓
2. Tomcat parses bytes → HttpServletRequest
          ↓
3. Tomcat calls DispatcherServlet.service()
          ↓
4. DispatcherServlet delegates to your @Controller method
          ↓
5. Your method returns data (e.g., a DTO object)
          ↓
6. Jackson serializes the DTO to JSON
          ↓
7. Spring writes JSON to HttpServletResponse
          ↓
8. Tomcat sends bytes back to client
```

---

## Filters vs. Interceptors

Two ways to intercept requests before/after your controller:

| Concept | Java/Spring | Node.js equiv |
|---------|-------------|---------------|
| **Filter** | `jakarta.servlet.Filter` | Express `app.use()` middleware |
| **Interceptor** | Spring `HandlerInterceptor` | NestJS `interceptor` / `guard` |

**Filters** run at the Servlet container level — before Spring even sees the request. Used for: logging, CORS, JWT parsing.

**Interceptors** run inside the Spring MVC layer — after DispatcherServlet picks a handler, before/after the handler runs.

```java
// A simple logging filter
@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        System.out.println("Incoming: " + req.getMethod() + " " + req.getRequestURI());
        
        chain.doFilter(request, response); // continue to next filter/servlet
        
        System.out.println("Outgoing: " + ((HttpServletResponse) response).getStatus());
    }
}
```

In Spring Security (Module 5), `JwtAuthenticationFilter` is a Filter — it runs before any controller to parse and validate JWT tokens.

---

## How Spring Boot Bootstraps the Servlet Container

In your `main` class:

```java
@SpringBootApplication
public class TaskforgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskforgeApplication.class, args); // ← this starts Tomcat
    }
}
```

`SpringApplication.run()` internally:
1. Creates an `ApplicationContext`
2. Detects `spring-boot-starter-web` on the classpath
3. Creates an embedded `TomcatServletWebServerFactory`
4. Starts Tomcat on port `server.port` (default 8080)
5. Registers `DispatcherServlet` to handle all requests

You can see this in startup logs:
```
Tomcat started on port(s): 8080 (http) with context path ''
Started TaskforgeApplication in 2.341 seconds
```

---

## HttpServletRequest & HttpServletResponse

You rarely use these directly in Spring MVC controllers — Spring unwraps them into convenient method parameters. But you can inject them if needed:

```java
@GetMapping("/debug")
public String debug(HttpServletRequest request, HttpServletResponse response) {
    String userAgent = request.getHeader("User-Agent");
    String clientIp  = request.getRemoteAddr();
    response.setHeader("X-Custom", "value");
    return "Debug info logged";
}
```

**Node.js equiv:** Accessing `req.headers['user-agent']` or `req.ip` in Express.

---

## Try It Yourself

**Exercise: Observe the Filter Chain**

Add this filter to your TaskForge project and watch the console when you start it up and make a request:

```java
// src/main/java/com/taskforge/filter/RequestLoggingFilter.java
package com.taskforge.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        long start = System.currentTimeMillis();
        System.out.printf("[Filter] --> %s %s%n", req.getMethod(), req.getRequestURI());
        
        chain.doFilter(request, response);
        
        long duration = System.currentTimeMillis() - start;
        System.out.printf("[Filter] <-- %s %s (%dms)%n", req.getMethod(), req.getRequestURI(), duration);
    }
}
```

Hit any endpoint and you'll see:
```
[Filter] --> GET /actuator/health
[Filter] <-- GET /actuator/health (12ms)
```

---

## Capstone Connection

**TaskForge uses this:** The JWT authentication filter (`JwtAuthenticationFilter`) in Module 5 runs at the Filter level — it inspects the `Authorization: Bearer <token>` header before any controller is reached. Understanding the Servlet → DispatcherServlet → Controller chain explains exactly where security checks happen.

**Next:** [02 — Spring MVC Architecture](./02-architecture.md) — how DispatcherServlet dispatches to your @Controller methods.
