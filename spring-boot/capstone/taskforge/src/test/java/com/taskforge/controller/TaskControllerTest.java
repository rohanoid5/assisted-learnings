package com.taskforge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.domain.Priority;
import com.taskforge.domain.Role;
import com.taskforge.domain.TaskStatus;
import com.taskforge.domain.User;
import com.taskforge.dto.task.CreateTaskRequest;
import com.taskforge.dto.task.TaskResponse;
import com.taskforge.security.JwtAuthenticationEntryPoint;
import com.taskforge.security.JwtAuthenticationFilter;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.TaskService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TaskService taskService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserPrincipal mockPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        User user = User.builder().id(1L).name("Alice").email("alice@example.com")
                .passwordHash("hash").role(Role.USER).enabled(true).build();
        mockPrincipal = UserPrincipal.from(user);
        // Configure the filter mock to forward requests to the next filter in the chain.
        // Without this, Mockito's inline mock maker intercepts the final doFilter() method
        // and silently drops requests, returning an empty 200 response.
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private TaskResponse sampleTask() {
        return new TaskResponse(1L, "Fix bug", "Critical bug",
                TaskStatus.TODO, Priority.HIGH, null,
                10L, "TaskForge",
                1L, "Alice", null, null,
                0L, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void getTask_shouldReturnTask() throws Exception {
        given(taskService.getTaskById(eq(1L), any())).willReturn(sampleTask());

        mockMvc.perform(get("/api/tasks/1").with(user(mockPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Fix bug"))
                .andExpect(jsonPath("$.data.status").value("TODO"));
    }

    @Test
    void createTask_withValidRequest_shouldReturn201() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                "Fix bug", "Critical bug", Priority.HIGH, null, 10L, null);

        given(taskService.createTask(any(), any())).willReturn(sampleTask());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(mockPrincipal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void createTask_withMissingTitle_shouldReturn400() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest(
                "", null, Priority.MEDIUM, null, 10L, null);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(mockPrincipal))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTasks_shouldReturnPagedResults() throws Exception {
        var page = new PageImpl<>(List.of(sampleTask()), PageRequest.of(0, 20), 1);
        given(taskService.listTasksByProject(anyLong(), any(), any(), any(), any())).willReturn(page);

        mockMvc.perform(get("/api/tasks").param("projectId", "10").with(user(mockPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getTask_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isUnauthorized());
    }
}

