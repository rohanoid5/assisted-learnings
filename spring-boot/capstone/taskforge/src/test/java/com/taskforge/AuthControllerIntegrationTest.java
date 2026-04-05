package com.taskforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.dto.auth.JwtResponse;
import com.taskforge.dto.auth.LoginRequest;
import com.taskforge.dto.auth.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // Shared state across ordered tests
    static String accessToken;

    @Test
    @Order(1)
    void register_withValidData_shouldReturn201AndToken() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@integration.com", "password123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("accessToken");
    }

    @Test
    @Order(2)
    void register_withDuplicateEmail_shouldReturn400() {
        RegisterRequest request = new RegisterRequest("Alice2", "alice@integration.com", "password123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    void login_withValidCredentials_shouldReturnToken() throws Exception {
        LoginRequest request = new LoginRequest("alice@integration.com", "password123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = objectMapper.readTree(response.getBody());
        accessToken = body.at("/data/accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(4)
    void protectedEndpoint_withValidToken_shouldReturn200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/projects", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(5)
    void protectedEndpoint_withoutToken_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/projects", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
