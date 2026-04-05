# 02 — Spring MVC Architecture

## The Big Picture

Spring MVC is a **request-driven web framework** built on the Servlet API. Its architecture is centered on one class: **DispatcherServlet**.

```
                          Spring MVC Architecture
┌─────────────────────────────────────────────────────────────────────┐
│                         Tomcat (Servlet Container)                   │
│                                                                      │
│   HTTP Request → DispatcherServlet → HandlerMapping → Controller    │
│                                                         │            │
│   HTTP Response ← View/JSON ←── MessageConverter ←── Return Value  │
└─────────────────────────────────────────────────────────────────────┘
```

**Node.js mental model:**
```
HTTP Request → Express app → Router → Route handler → res.json()
```

DispatcherServlet is like Express's `app` — it receives every request and delegates to the right handler.

---

## The 7-Step DispatcherServlet Lifecycle

Here is what happens, in order, when a request hits your Spring Boot app:

```
 Client
   │
   ▼
1. DispatcherServlet.service() is invoked by Tomcat
   │
   ▼
2. HandlerMapping: "Which handler (controller method) matches this URL + HTTP method?"
   │   e.g., GET /api/tasks/42  →  TaskController.getTask(Long id)
   ▼
3. HandlerAdapter: "How do I call that handler?" (resolves method arguments)
   │   @PathVariable id=42, @RequestBody parsed to DTO, etc.
   ▼
4. Controller method executes (your code runs here)
   │   Returns: a DTO, ResponseEntity, String, void, etc.
   ▼
5. HandlerExceptionResolver: if an exception was thrown, handle it here
   │   @ControllerAdvice methods run here
   ▼
6. HttpMessageConverter: serialize the return value to the response body
   │   DTO → Jackson → JSON bytes
   ▼
7. Response committed: Tomcat sends bytes to client
```

The beauty: steps 2, 3, 5, 6 are handled by Spring. **Your code only lives in step 4.**

---

## Key Components

### HandlerMapping

Maps incoming requests to handler methods. The most common implementation is `RequestMappingHandlerMapping`, which reads your `@GetMapping`, `@PostMapping`, etc. annotations.

```java
@GetMapping("/api/tasks/{id}")   // ← HandlerMapping reads this
public TaskResponse getTask(@PathVariable Long id) { ... }
```

Multiple HandlerMappings can exist; DispatcherServlet tries them in priority order.

### HandlerAdapter

Knows how to *invoke* the handler. `RequestMappingHandlerAdapter` handles methods annotated with request mappings. It:
- Resolves `@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader` into method arguments
- Handles return types: `ResponseEntity<T>`, `T`, `void`, etc.

### HttpMessageConverter

Converts Java objects ↔ HTTP request/response bodies.

| Converter | Handles |
|-----------|---------|
| `MappingJackson2HttpMessageConverter` | JSON (`application/json`) |
| `StringHttpMessageConverter` | Plain text |
| `ByteArrayHttpMessageConverter` | Binary data |
| `FormHttpMessageConverter` | Form data (`application/x-www-form-urlencoded`) |

When your controller returns a `TaskResponse` DTO, Jackson's converter serializes it to JSON. When a `@RequestBody TaskRequest` arrives, Jackson deserializes the JSON body to your DTO.

**Node.js equiv:** `express.json()` middleware + `res.json()` — Spring does this automatically based on `Accept` / `Content-Type` headers.

### HandlerExceptionResolver

Catches exceptions thrown during controller execution and translates them to HTTP responses. Your `@ControllerAdvice` class is registered here.

### ViewResolver

For classical server-side HTML rendering (JSP/Thymeleaf). In pure REST APIs (what we build in TaskForge), this is bypassed because `@RestController` uses HttpMessageConverter directly.

---

## Request Lifecycle: Full Walkthrough

Let's trace `GET /api/tasks/42` through the system:

```
Client: GET /api/tasks/42
        Authorization: Bearer <jwt>

Tomcat: parses request → calls DispatcherServlet.service()

DispatcherServlet:
  1. HandlerMapping scans → finds TaskController.getTask(Long) for GET /api/tasks/{id}
  2. Security Filter (runs before this): validates JWT, sets SecurityContext
  3. HandlerAdapter resolves: @PathVariable id = 42L
  4. TaskController.getTask(42L) executes:
       - calls taskService.findById(42L)
       - taskService queries DB via taskRepository
       - returns Optional<Task> → maps to TaskResponse DTO
       - controller returns ResponseEntity.ok(taskResponse)
  5. No exception thrown
  6. Jackson serializes TaskResponse → {"id":42,"title":"...","status":"TODO",...}
  7. HTTP 200 + JSON body sent to client
```

---

## @Controller vs @RestController

```java
// @Controller — returns view names (for HTML rendering)
@Controller
public class PageController {
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("user", currentUser);
        return "dashboard"; // ← ViewResolver looks for dashboard.html/dashboard.jsp
    }
}

// @RestController — returns data, serialized to JSON/XML
// @RestController = @Controller + @ResponseBody on every method
@RestController
public class TaskController {
    @GetMapping("/api/tasks/{id}")
    public TaskResponse getTask(@PathVariable Long id) {
        return taskService.findById(id); // ← Jackson auto-serializes to JSON
    }
}
```

For TaskForge (a REST API), we use **@RestController exclusively**.

---

## Content Negotiation

Spring MVC automatically handles content negotiation using the `Accept` header:

```
Client sends: Accept: application/json
→ Spring uses MappingJackson2HttpMessageConverter → returns JSON

Client sends: Accept: application/xml
→ Spring uses Jaxb2RootElementHttpMessageConverter → returns XML (if jackson-dataformat-xml is on classpath)
```

In practice, every API client sends `Accept: application/json`, so you'll mostly never think about this.

---

## MVC with Jackson Configuration

Jackson's ObjectMapper can be customized in `application.yml`:

```yaml
spring:
  jackson:
    # Serialize dates as ISO-8601 strings, not timestamps
    serialization:
      write-dates-as-timestamps: false
    # Don't fail when response has unknown fields
    deserialization:
      fail-on-unknown-properties: false
    # Use camelCase in JSON (default)
    property-naming-strategy: LOWER_CAMEL_CASE
    # Don't include null fields in JSON
    default-property-inclusion: non_null
```

Or in Java:

```java
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

---

## Spring MVC vs. Express: Component Mapping

| Spring MVC | Express/Node.js | Role |
|------------|-----------------|------|
| DispatcherServlet | `app` | Front controller / request entry point |
| `@RestController` | `Router` + route handlers | Groups related endpoints |
| `@GetMapping`, `@PostMapping` | `router.get()`, `router.post()` | Route definition |
| `HandlerMapping` | Express routing layer | Maps URL patterns to handlers |
| `HttpMessageConverter` | `express.json()` + `res.json()` | Serialize/deserialize bodies |
| `@ControllerAdvice` | Error middleware `(err, req, res, next)` | Global error handling |
| `Filter` | `app.use(middleware)` | Pre/post request processing |
| `@Valid` + Bean Validation | Joi / Zod / class-validator | Input validation |

---

## Try It Yourself

Run your Spring Boot app with the `--debug` flag to see DispatcherServlet internals:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.web.servlet=TRACE
```

Or add to `application-dev.yml`:
```yaml
logging:
  level:
    org.springframework.web.servlet: DEBUG
    org.springframework.web.servlet.mvc.method.annotation: DEBUG
```

You'll see logs like:
```
DispatcherServlet: GET "/api/tasks/42"
Mapped to com.taskforge.controller.TaskController#getTask(Long)
Using @ResponseBody with MappingJackson2HttpMessageConverter
```

---

## Capstone Connection

**TaskForge's full request flow:**

```
Client: POST /api/auth/login
         Content-Type: application/json
         Body: {"email":"user@example.com","password":"secret"}

┌─────── JwtAuthenticationFilter (Filter, Module 5) ────────────────┐
│ No Authorization header → skip JWT validation, pass through        │
└────────────────────────────────────────────────────────────────────┘
         ↓
┌─────── DispatcherServlet ──────────────────────────────────────────┐
│ HandlerMapping: POST /api/auth/login → AuthController.login()      │
│ HandlerAdapter: @RequestBody LoginRequest dto                       │
│ Controller: authService.login(dto) → returns JwtResponse           │
│ Jackson: JwtResponse → {"accessToken":"...","tokenType":"Bearer"}  │
└────────────────────────────────────────────────────────────────────┘
         ↓
Client: HTTP 200 + JSON body
```

**Next:** [03 — Spring MVC Components](./03-components.md) — controllers, services, repositories, and their interactions.
