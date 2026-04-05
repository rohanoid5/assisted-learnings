package com.taskforge.dto.task;

import com.taskforge.domain.Priority;
import com.taskforge.domain.TaskStatus;
import java.time.LocalDateTime;

public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    LocalDateTime dueDate,
    Long projectId,
    String projectName,
    Long creatorId,
    String creatorName,
    Long assigneeId,
    String assigneeName,
    long commentCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
