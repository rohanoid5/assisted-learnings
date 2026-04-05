package com.taskforge.dto.task;

import com.taskforge.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
    @NotNull(message = "Status is required")
    TaskStatus status
) {}
