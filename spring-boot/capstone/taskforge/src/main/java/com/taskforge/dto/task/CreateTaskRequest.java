package com.taskforge.dto.task;

import com.taskforge.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateTaskRequest(
    @NotBlank(message = "Task title is required")
    @Size(min = 1, max = 500)
    String title,

    @Size(max = 5000)
    String description,

    @NotNull(message = "Priority is required")
    Priority priority,

    LocalDateTime dueDate,

    @NotNull(message = "Project ID is required")
    Long projectId,

    Long assigneeId   // optional
) {}
