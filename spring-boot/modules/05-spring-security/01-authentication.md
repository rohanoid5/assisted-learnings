# 01 — Authentication

## What Is Authentication?

**Authentication** = "Who are you?" — verifying identity.
**Authorization** = "What can you do?" — verifying permissions. (Next file)

**Node.js mental model:**

| Node.js (Passport.js) | Spring Security |
|----------------------|-----------------|
| `passport.use(new LocalStrategy(...))` | `UserDetailsService.loadUserByUsername()` |
| `passport.authenticate('local')` | `AuthenticationManager.authenticate()` |
| `bcrypt.compare(password, hash)` | `PasswordEncoder.matches()` |
| `req.user` | `SecurityContextHolder.getContext().getAuthentication()` |
| `express-session` | Spring Session or JWT (stateless) |

---

## Spring Security Architecture

Spring Security operates as a chain of **Filters** (review Module 3, File 01). Every HTTP request passes through this chain before reaching your controller.

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────┐
│               SecurityFilterChain                     │
│                                                       │
│  1. SecurityContextPersistenceFilter                  │
│     (loads SecurityContext from session/JWT)          │
│                                                       │
│  2. UsernamePasswordAuthenticationFilter (form login) │
│     OR your custom JwtAuthenticationFilter            │
│                                                       │
│  3. ExceptionTranslationFilter                        │
│     (converts auth exceptions to 401/403)             │
│                                                       │
│  4. FilterSecurityInterceptor                         │
│     (checks if authenticated user has required role)  │
└─────────────────────────────────────────────────────┘
     │
     ▼
DispatcherServlet → @RestController
```

---

## Setup: Add Spring Security

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**What happens immediately after adding this starter:**
- All endpoints return 401 unless authenticated
- Spring auto-generates a random password (printed to console)
- Form-login page appears at `/login`

We'll replace all of this with JWT.

---

## UserDetails and UserDetailsService

Spring Security needs to know how to load a user. Implement `UserDetailsService`:

```java
// src/main/java/com/taskforge/security/UserDetailsServiceImpl.java
package com.taskforge.security;

import com.taskforge.domain.User;
import com.taskforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new UserPrincipal(user);
    }
}
```

**UserPrincipal — wraps your User entity as a Spring Security principal:**

```java
// src/main/java/com/taskforge/security/UserPrincipal.java
package com.taskforge.security;

import com.taskforge.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        // Spring Security roles must be prefixed with "ROLE_"
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

---

## PasswordEncoder

Never store plain-text passwords. Use BCrypt:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // cost factor: higher = slower = more secure
}
```

**Usage:**
```java
// When registering a new user:
String hashedPassword = passwordEncoder.encode(rawPassword);
user.setPassword(hashedPassword);

// When verifying login:
boolean valid = passwordEncoder.matches(rawPassword, storedHash);
```

**Node.js equiv:**
```javascript
const hash = await bcrypt.hash(password, 12);       // encode
const valid = await bcrypt.compare(password, hash); // matches
```

---

## AuthService — Registration and Login

```java
// src/main/java/com/taskforge/service/AuthService.java
package com.taskforge.service;

import com.taskforge.domain.User;
import com.taskforge.dto.request.LoginRequest;
import com.taskforge.dto.request.RegisterRequest;
import com.taskforge.dto.response.JwtResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.repository.UserRepository;
import com.taskforge.security.JwtTokenProvider;
import com.taskforge.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .password(passwordEncoder.encode(request.password())) // HASH the password
            .role(com.taskforge.domain.Role.USER)
            .build();

        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
    }

    public JwtResponse login(LoginRequest request) {
        // Throws BadCredentialsException if wrong credentials — Spring handles to 401
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        String token = jwtTokenProvider.generateToken(principal);
        return new JwtResponse(token);
    }
}
```

---

## SecurityConfig — The Central Configuration

```java
// src/main/java/com/taskforge/config/SecurityConfig.java
package com.taskforge.config;

import com.taskforge.security.JwtAuthenticationFilter;
import com.taskforge.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize, @PostAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT APIs
            .csrf(csrf -> csrf.disable())

            // Stateless — no sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()  // CORS preflight

                // Swagger UI (if using springdoc)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Add JWT filter before the standard auth filter
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)

            // Return 401 for unauthenticated requests (not redirect to login page)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
            );

        return http.build();
    }
}
```

---

## The Authentication Flow (End to End)

```
1. POST /api/auth/login {"email":"alice@example.com","password":"secret"}
         ↓
2. AuthController.login(LoginRequest)
         ↓
3. AuthService.login() → authenticationManager.authenticate(...)
         ↓
4. DaoAuthenticationProvider calls userDetailsService.loadUserByUsername("alice@example.com")
         ↓
5. UserDetailsServiceImpl loads User from DB, returns UserPrincipal
         ↓
6. DaoAuthenticationProvider calls passwordEncoder.matches("secret", storedBcryptHash)
         ↓
7. If match: returns authenticated Authentication object
   If no match: throws BadCredentialsException → 401
         ↓
8. AuthService.login() calls jwtTokenProvider.generateToken(principal) → JWT string
         ↓
9. Returns JwtResponse {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":86400}
```

---

## Try It Yourself

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}' | jq

# Login — save the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}' | jq -r '.data.accessToken')

echo "Token: $TOKEN"

# Try protected endpoint without token
curl -s http://localhost:8080/api/tasks?projectId=1
# → 401 Unauthorized

# Try with token
curl -s "http://localhost:8080/api/tasks?projectId=1" \
  -H "Authorization: Bearer $TOKEN" | jq
# → 200 + tasks
```

---

## Capstone Connection

Authentication is now fully wired in TaskForge:
- `POST /api/auth/register` → creates user with BCrypt password
- `POST /api/auth/login` → validates credentials → returns JWT
- All other endpoints require `Authorization: Bearer <token>` header
- `@AuthenticationPrincipal UserPrincipal currentUser` in controllers now works

**Next:** [02 — Authorization](./02-authorization.md) — role-based access control with @PreAuthorize.
