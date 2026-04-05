# 04 — JWT Authentication

## What Is a JWT?

A **JSON Web Token** is a compact, self-contained token for transmitting claims between parties.

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwibmFtZSI6IkFsaWNlIiwicm9sZSI6IkFETUlOIiwiaWF0IjoxNzA2OTIwMDAwLCJleHAiOjE3MDcwMDY0MDB9.signature
│──────────────────────│ │─────────────────────────────────────────────────────────────────────│ │─────────│
       Header                                    Payload                                          Signature
```

**Three parts, Base64URL encoded, separated by dots.**

### Header
```json
{
  "alg": "HS512",   // Signing algorithm
  "typ": "JWT"
}
```

### Payload (claims)
```json
{
  "sub": "1",            // subject (user ID)
  "name": "Alice",
  "role": "ADMIN",
  "iat": 1706920000,     // issued at (Unix timestamp)
  "exp": 1707006400      // expires at (24 hours later)
}
```

### Signature
```
HMACSHA512(base64(header) + "." + base64(payload), secretKey)
```

The signature proves the token wasn't tampered with. Without the secret key, you can't forge a valid signature.

---

## JWT vs Session Tokens

| | Session (Stateful) | JWT (Stateless) |
|-|-------------------|----------------|
| Server stores state | ✅ (session DB/memory) | ❌ (token is self-contained) |
| Invalidation | Easy (delete session) | Hard (need blacklist) |
| Scalability | Needs sticky sessions or shared store | Any server can validate |
| Performance | DB lookup per request | Just crypto verification |
| Size | Small (session ID) | Larger (all claims encoded) |

**For REST APIs:** JWT is the standard. TaskForge uses JWTs.

---

## Dependencies

```xml
<!-- JJWT — Java JWT library (0.12.x API) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

---

## Configuration

```yaml
# application.yml
app:
  jwt:
    secret: ${JWT_SECRET:your-very-long-secret-key-at-least-64-characters-for-hs512-algorithm}
    expiration-ms: 86400000   # 24 hours = 24 * 60 * 60 * 1000
```

> **Security:** Never hardcode JWT secrets. Use environment variables in production. The default value here is only for local development.

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long expirationMs
) {}
```

Enable in your main class or any @Configuration:
```java
@EnableConfigurationProperties(JwtProperties.class)
```

---

## JwtTokenProvider

```java
// src/main/java/com/taskforge/security/JwtTokenProvider.java
package com.taskforge.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());

        return Jwts.builder()
            .subject(String.valueOf(userPrincipal.getId()))   // user ID as subject
            .claim("name", userPrincipal.getName())
            .claim("email", userPrincipal.getEmail())
            .claim("role", userPrincipal.getAuthorities()
                .iterator().next().getAuthority()             // "ROLE_ADMIN", etc.
                .replace("ROLE_", ""))                        // store as "ADMIN"
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())                        // HS512 by default
            .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(
            getClaims(token).getSubject()
        );
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);  // throws if invalid or expired
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token is expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT token is unsupported: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT token is malformed: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.warn("JWT signature validation failed: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token is empty or null: {}", ex.getMessage());
        }
        return false;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

---

## JwtAuthenticationFilter

This Filter runs on every request, reads the JWT from the `Authorization` header, validates it, and sets the `SecurityContext`:

```java
// src/main/java/com/taskforge/security/JwtAuthenticationFilter.java
package com.taskforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);

                // Load UserDetails from DB (gets fresh role + enabled status)
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,                            // no credentials needed
                        userDetails.getAuthorities()
                    );

                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set the authentication in SecurityContext for this request
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
            // Don't rethrow — let the request continue (will be rejected by FilterSecurityInterceptor as 401)
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // strip "Bearer " prefix
        }
        return null;
    }
}
```

---

## Add loadUserById to UserDetailsServiceImpl

```java
// Add this method to UserDetailsServiceImpl.java
@Transactional(readOnly = true)
public UserDetails loadUserById(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    return new UserPrincipal(user);
}
```

> **Why load from DB on every request?** To get the latest role and enabled status. If you want maximum performance, you can extract claims from the JWT itself and skip the DB call — but then role changes won't take effect until the JWT expires.

---

## The Complete JWT Request Flow

```
Client: GET /api/tasks?projectId=1
        Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...

  ┌─── JwtAuthenticationFilter ───────────────────────────────────────┐
  │ 1. Extract token from Authorization header                         │
  │ 2. jwtTokenProvider.validateToken(token) → true                   │
  │ 3. getUserIdFromToken(token) → 1L                                  │
  │ 4. userDetailsService.loadUserById(1L) → UserPrincipal(alice, ADMIN)│
  │ 5. Set SecurityContext with alice's Authentication object          │
  └───────────────────────────────────────────────────────────────────┘

  ┌─── SecurityFilterChain ──────────────────────────────────────────┐
  │ Check: .anyRequest().authenticated() → alice is authenticated ✅  │
  └───────────────────────────────────────────────────────────────────┘

  ┌─── TaskController.getTasks() ─────────────────────────────────────┐
  │ @AuthenticationPrincipal UserPrincipal currentUser               │
  │ → currentUser.getId() = 1L                                        │
  │ → currentUser.getEmail() = "alice@example.com"                    │
  └───────────────────────────────────────────────────────────────────┘
```

---

## Token Refresh Strategy

JWTs can't be invalidated server-side (no state). Common strategies:

**Short-lived access token + refresh token:**
```
Access token:  15 minutes (can't be revoked, but expires quickly)
Refresh token: 30 days (stored in DB, can be revoked)

Client stores both. When access token expires:
→ POST /api/auth/refresh { "refreshToken": "..." }
→ Server validates refresh token in DB, issues new access token
→ Client uses new access token
```

**Implementation outline:**
```java
@Transactional
public JwtResponse refresh(String refreshToken) {
    RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    
    if (token.getExpiresAt().isBefore(Instant.now())) {
        refreshTokenRepository.delete(token);
        throw new RuntimeException("Refresh token expired");
    }
    
    String newAccessToken = jwtTokenProvider.generateToken(
        new UserPrincipal(token.getUser())
    );
    return new JwtResponse(newAccessToken);
}
```

**For TaskForge:** We use a 24-hour access token for simplicity. Add refresh tokens as enhancement after the tutorial.

---

## Security Best Practices

| Practice | Why |
|----------|-----|
| **Use HS512 or RS256** | HS256 is fine for single-server; RS256 for microservices (verify with public key) |
| **Never log JWT tokens** | They're credentials — log only userId from claims |
| **Short expiration** | 15min–24hr depending on use case |
| **HTTPS only** | JWT in plaintext is readable by anyone with network access |
| **Don't put PII in payload** | Payload is Base64 encoded, not encrypted — readable by client |
| **Rotate secret keys** | Use a key management service in production |
| **Validate all claims** | Check `exp`, `iat`, signature — JJWT validates all by default |
| **Store in httpOnly cookie or memory** | Never localStorage (XSS risk) |

---

## Try It Yourself

Complete the full authentication flow:

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123!"}' | jq

# 2. Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123!"}' | jq -r '.data.accessToken')

# 3. Decode the payload (Base64 decode middle section)
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq

# Should show:
# {
#   "sub": "1",
#   "name": "Alice",
#   "email": "alice@example.com",
#   "role": "USER",
#   "iat": ...,
#   "exp": ...
# }

# 4. Use the token
curl -s "http://localhost:8080/api/tasks?projectId=1" \
  -H "Authorization: Bearer $TOKEN" | jq

# 5. Try expired/invalid token
curl -s "http://localhost:8080/api/tasks?projectId=1" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.invalid.signature"
# → 401 Unauthorized
```

---

## Module 5 Exercises

See [exercises/README.md](./exercises/README.md) for hands-on exercises that wire up the full security layer.

---

## Capstone Connection

The complete security layer for TaskForge:

```
capstone/taskforge/src/main/java/com/taskforge/
├── config/
│   └── SecurityConfig.java         ← FilterChain definition
├── security/
│   ├── UserPrincipal.java          ← UserDetails implementation
│   ├── UserDetailsServiceImpl.java  ← loadUserByUsername() + loadUserById()
│   ├── JwtTokenProvider.java        ← generate + validate tokens
│   ├── JwtAuthenticationFilter.java ← per-request JWT validation
│   └── JwtProperties.java           ← @ConfigurationProperties
└── service/
    └── AuthService.java             ← register() + login()
```

**Next:** [Module 5 Exercises](./exercises/README.md) — implement and test the full auth flow.
