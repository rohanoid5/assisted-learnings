# Module 5 Exercises — Spring Security

Build the complete security layer for TaskForge, from JWT generation through method-level authorization.

---

## Exercise 1 — Implement the JWT Provider

**Goal:** Wire up `JwtTokenProvider` and verify it generates parseable tokens.

**Step 1:** Add to `application.yml`:
```yaml
app:
  jwt:
    secret: "dev-secret-key-please-change-this-in-production-must-be-at-least-64-chars"
    expiration-ms: 86400000
```

**Step 2:** Create `JwtProperties.java`:
```java
package com.taskforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long expirationMs
) {}
```

**Step 3:** Enable it — add to `TaskforgeApplication.java`:
```java
@EnableConfigurationProperties(JwtProperties.class)
```

**Step 4:** Implement `JwtTokenProvider` and `JwtAuthenticationFilter` exactly as shown in [04-jwt-authentication.md](../04-jwt-authentication.md).

**Step 5:** Add `loadUserById` to `UserDetailsServiceImpl`.

**Verify with a unit test:**
```java
@SpringBootTest
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldGenerateAndParseToken() {
        // Arrange — stub UserPrincipal using a User entity
        User user = User.builder()
            .id(42L)
            .name("Alice")
            .email("alice@test.com")
            .role(Role.USER)
            .build();
        UserPrincipal principal = new UserPrincipal(user);

        // Act
        String token = jwtTokenProvider.generateToken(principal);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void shouldRejectTamperedToken() {
        String fakeToken = "eyJhbGciOiJIUzUxMiJ9.tampered.invalidsig";
        assertThat(jwtTokenProvider.validateToken(fakeToken)).isFalse();
    }
}
```

---

## Exercise 2 — Test Auth Endpoints with curl

**Goal:** Exercise the full register → login → use flow against a running server.

**Requirements:** Module 4 persistence layer wired up, PostgreSQL running.

```bash
# Start PostgreSQL
docker start taskforge-db || \
  docker run -d --name taskforge-db \
    -e POSTGRES_DB=taskforge -e POSTGRES_USER=taskforge \
    -e POSTGRES_PASSWORD=taskforge_dev -p 5432:5432 postgres:15-alpine

# Start App
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","email":"bob@example.com","password":"SecurePass123!"}' | jq .

# Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","password":"SecurePass123!"}' | jq -r '.data.accessToken')

echo "Token: $TOKEN"

# Decode payload (view claims without signature verification)
echo $TOKEN | cut -d'.' -f2 | base64 --decode 2>/dev/null | jq .

# Access a protected endpoint
curl -s http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" | jq .

# Access WITHOUT token → should get 401
curl -s http://localhost:8080/api/projects | jq .
```

**Expected 401 response shape:**
```json
{
  "success": false,
  "data": null,
  "message": "Unauthorized",
  "timestamp": "..."
}
```

---

## Exercise 3 — Add @PreAuthorize to Project Endpoints

**Goal:** Enforce fine-grained authorization using method security.

**Step 1:** Ensure `@EnableMethodSecurity` is on your `SecurityConfig`.

**Step 2:** Update `ProjectController`:
```java
// GET /api/projects/{id} — any authenticated member can view
@GetMapping("/{id}")
@PreAuthorize("@projectSecurityService.isMember(#id, authentication.principal.id)")
public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long id) { ... }

// PUT /api/projects/{id} — only owner or admin
@PutMapping("/{id}")
@PreAuthorize("@projectSecurityService.isOwner(#id, authentication.principal.id) or hasRole('ADMIN')")
public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
    @PathVariable Long id, @RequestBody @Valid UpdateProjectRequest request) { ... }

// DELETE /api/projects/{id} — only admin
@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) { ... }
```

**Step 3:** Test that an unauthorized user gets 403:
```bash
# Login as regular USER (not owner)
TOKEN_USER=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -d '{"email":"user@example.com","password":"pass"}' \
  -H "Content-Type: application/json" | jq -r '.data.accessToken')

# Try to delete (should be 403 — only ADMIN allowed)
curl -s -X DELETE http://localhost:8080/api/projects/1 \
  -H "Authorization: Bearer $TOKEN_USER" | jq .
```

---

## Exercise 4 — Implement ProjectSecurityService

**Goal:** Write the custom security bean that evaluates project membership.

```java
package com.taskforge.security;

import com.taskforge.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("projectSecurityService")
@RequiredArgsConstructor
public class ProjectSecurityService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public boolean isOwner(Long projectId, Long userId) {
        // TODO: check if user is the project owner
        // Hint: projectRepository.findById(projectId).map(p -> p.getOwner().getId().equals(userId))
        throw new UnsupportedOperationException("Implement me!");
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long projectId, Long userId) {
        // TODO: check if user is a member (or owner) of the project
        // Hint: use a custom repository query or check the members collection
        throw new UnsupportedOperationException("Implement me!");
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOrMember(Long projectId, Long userId) {
        return isOwner(projectId, userId) || isMember(projectId, userId);
    }
}
```

Add a matching repository method:
```java
// ProjectRepository.java
@Query("SELECT COUNT(p) > 0 FROM Project p JOIN p.members m WHERE p.id = :projectId AND m.id = :userId")
boolean isMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
```

---

## Exercise 5 — Security Integration Test

**Goal:** Write a `@SpringBootTest` that tests the full security layer.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void register_thenLogin_shouldReturnJwt() {
        // Register
        RegisterRequest registerReq = new RegisterRequest("Charlie", "charlie@test.com", "Password1!");
        ResponseEntity<ApiResponse> registerResp = restTemplate.postForEntity(
            "/api/auth/register", registerReq, ApiResponse.class);
        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Login
        LoginRequest loginReq = new LoginRequest("charlie@test.com", "Password1!");
        ResponseEntity<ApiResponse> loginResp = restTemplate.postForEntity(
            "/api/auth/login", loginReq, ApiResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) loginResp.getBody().data();
        assertThat(data.get("accessToken")).isNotNull();
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/projects", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_withValidToken_shouldReturn200() {
        // First register and login to get a token
        String token = loginAndGetToken("dave@test.com", "Password1!");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/projects", HttpMethod.GET, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String loginAndGetToken(String email, String password) {
        restTemplate.postForEntity("/api/auth/register",
            new RegisterRequest("Test User", email, password), ApiResponse.class);

        ResponseEntity<ApiResponse> loginResp = restTemplate.postForEntity(
            "/api/auth/login", new LoginRequest(email, password), ApiResponse.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) loginResp.getBody().data();
        return (String) data.get("accessToken");
    }
}
```

---

## Module 5 Capstone Checkpoint

Before moving to Module 6, verify these behaviors:

```
Authentication:
[ ] POST /api/auth/register creates a user with hashed password
[ ] POST /api/auth/login returns a JWT
[ ] JWT payload contains sub (userId), email, name, role
[ ] Duplicate email registration returns 400

Authorization:
[ ] GET /api/projects without token → 401
[ ] GET /api/projects with valid token → 200
[ ] DELETE /api/projects/{id} as USER → 403
[ ] DELETE /api/projects/{id} as ADMIN → 200 (or 404 if not found)

JWT:
[ ] Tampered token is rejected with 401
[ ] Expired token is rejected with 401
[ ] @AuthenticationPrincipal resolves to correct UserPrincipal in controllers
[ ] SecurityContextHolder.getContext().getAuthentication() non-null inside service
```

---

## Up Next

[Module 6 — Testing](../../06-testing/README.md) — unit tests, MockMvc, @DataJpaTest, and integration tests for everything you've built.
