# 2.3 — Embedded Server

## Concept

In traditional Java web development, you'd package your app as a `.war` file and deploy it to an externally running application server (Tomcat, JBoss, WebSphere). Spring Boot embeds the server **inside your application** — your JAR is self-contained and runnable anywhere Java is installed.

**Node.js analogy:** Node.js has always done this — `http.createServer()` is built in. Spring Boot brings that same convenience to Java. Before Spring Boot, Java apps needed an external Tomcat install the same way PHP needs an external nginx — a real operational burden.

```bash
# Node.js — built-in HTTP server
node index.js       # Server is embedded in the process

# Spring Boot — same idea
java -jar app.jar   # Tomcat is embedded in the JAR
```

---

## The Embedded Server Options

Spring Boot supports three embedded servers. The default is **Tomcat**.

| Server | Default | Notes |
|--------|---------|-------|
| **Tomcat** | ✅ Yes | Most widely used, excellent performance |
| **Jetty** | No | Lighter, good for embedded/OSGi environments |
| **Undertow** | No | Best throughput for high-concurrency, from WildFly |

**Switching to Undertow** (example):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <!-- Remove default Tomcat -->
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Add Undertow instead -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

For TaskForge (and most apps), Tomcat is the right choice — no change needed.

---

## Configuring the Embedded Server

All server settings go in `application.yml`:

```yaml
server:
  # Port — equivalent to process.env.PORT in Node.js
  port: 8080

  # Context path — prefix all endpoints with /api (optional)
  # With this, /projects becomes /api/projects
  # servlet:
  #   context-path: /api

  # Graceful shutdown: wait for in-flight requests before stopping
  shutdown: graceful

  # Connection settings
  tomcat:
    connection-timeout: 20s
    max-connections: 8192
    threads:
      max: 200        # Max concurrent threads (Node.js doesn't need this — single-threaded)
      min-spare: 10   # Minimum idle threads

  # SSL/TLS — for HTTPS
  # ssl:
  #   key-store: classpath:keystore.p12
  #   key-store-password: changeit
  #   key-store-type: PKCS12
```

---

## Threads vs Event Loop

This is the most important difference from Node.js:

**Node.js:**
```
Single thread + Event Loop
- Non-blocking I/O by design
- 1 process handles thousands of concurrent requests
- Slow CPU work blocks everyone (must use Worker Threads)
- Perfect for I/O-bound workloads
```

**Spring Boot / Tomcat:**
```
Thread-per-request model
- Each request gets its own thread (from a pool)
- Thread is blocked while waiting for DB, I/O, etc.
- Spring WebFlux (reactive) ≈ Node's event loop
- Perfect for CPU-bound and mixed workloads
- Spring Boot 3.2+ supports Virtual Threads (Java 21) — solves the blocking problem
```

For most CRUD applications like TaskForge, threads are completely fine. The thread-per-request model is easier to reason about (no callback/async complexity).

### Virtual Threads (Java 21 / Spring Boot 3.2+)

If you're on Java 21:
```yaml
spring:
  threads:
    virtual:
      enabled: true   # Use virtual threads — millions of cheap threads, like Node's event loop
```

This gives you the throughput benefits of the event loop without the async programming model.

---

## JAR vs WAR Packaging

```
JAR (default)                    WAR (legacy)
┌─────────────────┐              ┌─────────────────────┐
│  app.jar        │              │  app.war             │
│  ├── App code   │              │  ├── App code        │
│  ├── Libraries  │              │  └── No server       │
│  └── Tomcat ✅  │              │                      │
└────────┬────────┘              └──────────┬───────────┘
         │                                  │
         ▼                                  ▼
java -jar app.jar               External Tomcat Server
```

Use **JAR** (the default) — it's self-contained, simpler to deploy, and works great with Docker.

---

## How the App Starts

```java
// This is all you need — Spring Boot does the rest
@SpringBootApplication
public class TaskforgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskforgeApplication.class, args);
        // ^ This:
        // 1. Creates the ApplicationContext
        // 2. Runs autoconfiguration
        // 3. Starts embedded Tomcat
        // 4. Registers DispatcherServlet
        // 5. Prints "Started TaskforgeApplication in X.XXX seconds"
    }
}
```

**Startup time:** For a medium-sized app, Spring Boot starts in 2–5 seconds. Faster with GraalVM native compilation (~50ms).

**Customizing `SpringApplication`:**
```java
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(TaskforgeApplication.class);
    app.setBannerMode(Banner.Mode.OFF);  // Disable the ASCII banner
    app.setDefaultProperties(Map.of("server.port", "8080"));
    app.run(args);
}
```

---

## Building and Running the Fat JAR

```bash
# Build (produces target/taskforge-0.0.1-SNAPSHOT.jar — the "fat JAR")
mvn clean package

# Run
java -jar target/taskforge-0.0.1-SNAPSHOT.jar

# Run with profile override
java -jar target/taskforge-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Run with port override
java -jar target/taskforge-0.0.1-SNAPSHOT.jar --server.port=9090
```

The fat JAR contains everything — app code, all dependencies, and Tomcat. This is what you put in a Docker container.

---

## Try It Yourself

**Exercise:** Customize the embedded server.

1. Change the port to `9090` in `application-dev.yml`:
```yaml
server:
  port: 9090
```

2. Restart and verify: `curl http://localhost:9090/actuator/health`

3. Add graceful shutdown:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

4. Build the fat JAR and run it:
```bash
mvn clean package -DskipTests
java -jar target/taskforge-0.0.1-SNAPSHOT.jar
```

5. Check the JAR size — note how it includes Tomcat and all dependencies:
```bash
du -sh target/taskforge-0.0.1-SNAPSHOT.jar
```

<details>
<summary>Expected output</summary>

```bash
# Port change
curl http://localhost:9090/actuator/health
{"status":"UP"}

# JAR size (varies by dependencies)
20M  target/taskforge-0.0.1-SNAPSHOT.jar
```
</details>

---

## Capstone Connection

TaskForge runs on embedded Tomcat on port 8080. In Module 8, you'll package it as a fat JAR and run it inside a Docker container — exactly what production Spring Boot deployments look like.
