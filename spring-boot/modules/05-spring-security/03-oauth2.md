# 03 — OAuth2

## What Is OAuth2?

OAuth2 is an **authorization protocol** that lets users authenticate via a third-party provider (Google, GitHub, Okta) instead of creating a new username/password.

```
User → "Login with Google" → Google login page
                              ↓
                         User grants access
                              ↓
                    Google redirects with code
                              ↓
              Your app exchanges code for user info
                              ↓
                     Create/find user in DB
                              ↓
                      Issue your own JWT
```

---

## OAuth2 vs JWT (They're Not the Same)

| | OAuth2 | JWT |
|-|--------|-----|
| **What it is** | Protocol for delegated authorization | Token format |
| **Problem solved** | "Let this 3rd-party app read my GitHub repos" | Stateless session/auth token |
| **Who issues** | Google, GitHub, Okta, etc. | Your own server |
| **Use case** | Social login, 3rd-party API access | Your own API auth |

TaskForge uses **both**: OAuth2 for social login (optional), JWT for API authentication.

---

## Setup: Spring OAuth2 Client

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope:
              - user:email
              - read:user
```

Spring Boot autoconfigures Google and GitHub providers — you only need the `client-id` and `client-secret` from their developer consoles.

---

## OAuth2 Login Flow with Spring Security

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final CustomOAuth2UserService oAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            
            // OAuth2 login config
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(endpoint ->
                    endpoint.baseUri("/oauth2/authorize"))
                .redirectionEndpoint(endpoint ->
                    endpoint.baseUri("/oauth2/callback/*"))
                .userInfoEndpoint(userInfo ->
                    userInfo.userService(oAuth2UserService))  // custom user processing
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## Custom OAuth2UserService

When Spring gets user info from Google/GitHub, this service processes it:

```java
// src/main/java/com/taskforge/security/CustomOAuth2UserService.java
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String registrationId = request.getClientRegistration().getRegistrationId(); // "google" or "github"
        String email = extractEmail(oAuth2User, registrationId);
        String name  = extractName(oAuth2User, registrationId);

        // Find or create user
        User user = userRepository.findByEmail(email)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .name(name)
                    .email(email)
                    .password("")  // no local password for OAuth users
                    .role(Role.USER)
                    .build();
                return userRepository.save(newUser);
            });

        return new OAuth2UserPrincipal(user, oAuth2User.getAttributes());
    }

    private String extractEmail(OAuth2User user, String provider) {
        return switch (provider) {
            case "google" -> user.getAttribute("email");
            case "github" -> user.getAttribute("email");  // or additional API call
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    private String extractName(OAuth2User user, String provider) {
        return switch (provider) {
            case "google" -> user.getAttribute("name");
            case "github" -> user.getAttribute("login");
            default -> "User";
        };
    }
}
```

---

## OAuth2 Success Handler — Issue JWT After OAuth2 Login

After successful OAuth2 login, redirect the user with a JWT:

```java
// src/main/java/com/taskforge/security/OAuth2AuthenticationSuccessHandler.java
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(principal.toUserPrincipal());

        // Redirect to frontend with token in query param (or set as cookie)
        String redirectUrl = "http://localhost:3000/oauth2/callback?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
```

---

## OAuth2 Failure Handler

```java
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String redirectUrl = "http://localhost:3000/login?error=oauth2_failed";
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
```

---

## The Full OAuth2 Flow

```
1. User clicks "Login with Google" on your frontend
   → GET /oauth2/authorize/google

2. Spring redirects to Google:
   accounts.google.com/o/oauth2/auth?client_id=...&redirect_uri=...&scope=email+profile

3. User logs in on Google, grants access

4. Google redirects back:
   GET /oauth2/callback/google?code=4/0AbcXyz...

5. Spring exchanges code for access_token via Google's token endpoint

6. Spring calls Google's userinfo endpoint with the access_token

7. CustomOAuth2UserService.loadUser() processes the user info
   → finds or creates User in DB

8. OAuth2AuthenticationSuccessHandler issues a JWT
   → redirects to frontend: http://localhost:3000/oauth2/callback?token=eyJ...

9. Frontend stores the token and uses it for all subsequent API requests
```

---

## Setting Up Google OAuth2 (Quick Guide)

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a project → APIs & Services → Credentials
3. Create OAuth 2.0 Client ID → Web Application
4. Add authorized redirect URIs: `http://localhost:8080/oauth2/callback/google`
5. Copy Client ID and Client Secret
6. Add to `.env` or set environment variables:
   ```bash
   export GOOGLE_CLIENT_ID=your-client-id
   export GOOGLE_CLIENT_SECRET=your-client-secret
   ```

---

## OAuth2 Resource Server (API-to-API)

For machine-to-machine auth (your API consuming another OAuth2-protected API):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://accounts.google.com
          # Spring auto-discovers JWKS endpoint from issuer-uri
```

```java
.oauth2ResourceServer(oauth2 ->
    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
)
```

This is for when you're the **protected resource** (your API validates tokens issued by a 3rd-party auth server like Auth0 or Keycloak).

---

## OAuth2 vs Custom JWT: Which for TaskForge?

**TaskForge uses custom JWT** for API authentication because:
- We control the auth server (our own `/api/auth/login`)
- No third-party dependency
- Simple to implement and understand

**OAuth2 social login** is an optional addition in Module 8 (production features). The implementation above shows how to bolt it on when needed.

---

## Try It Yourself

Even without setting up actual Google credentials, you can see the OAuth2 flow:

1. Add the starter to pom.xml
2. Add placeholder credentials in application.yml
3. Visit `http://localhost:8080/oauth2/authorize/google` — Spring will redirect to Google
4. (Without real credentials, Google will reject — but you see the redirect URL Spring builds)

For a full test, create a free Google Cloud project and real credentials (the setup takes ~10 minutes).

---

## Capstone Connection

TaskForge's core auth uses custom JWT (File 04). OAuth2 social login is a production enhancement. The architecture is designed so you can add OAuth2 without changing any existing code — just add the `oauth2-client` starter, configure credentials, and wire up the success handler.

**Next:** [04 — JWT Authentication](./04-jwt-authentication.md) — the complete JWT implementation for TaskForge.
