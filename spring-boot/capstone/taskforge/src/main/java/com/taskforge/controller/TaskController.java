package com.taskforge.controller;

import com.taskforge.domain.TaskStatus;
import com.taskforge.dto.ApiResponse;
import com.taskforge.dto.task.CreateTaskRequest;
import com.taskforge.dto.task.TaskResponse;
import com.taskforge.dto.task.UpdateTaskRequest;
import com.taskforge.dto.task.UpdateTaskStatusRequest;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> listTasks(
            @RequestParam Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TaskResponse> tasks = taskService.listTasksByProject(projectId, status, assigneeId,
                principal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(tasks));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse task = taskService.getTaskById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse task = taskService.createTask(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(task, "Task created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse task = taskService.updateTask(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(task, "Task updated"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse task = taskService.updateTaskStatus(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(task, "Task status updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        taskService.deleteTask(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(null, "Task deleted"));
    }
}
