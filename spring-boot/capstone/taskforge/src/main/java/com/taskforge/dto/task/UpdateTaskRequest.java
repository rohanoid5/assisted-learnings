package com.taskforge.dto.task;

import com.taskforge.domain.Priority;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateTaskRequest(
    @Size(min = 1, max = 500)
    String title,

    @Size(max = 5000)
    String description,

    Priority priority,

    LocalDateTime dueDate,

    Long assigneeId
) {}
