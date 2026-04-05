package com.taskforge.dto.project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectResponse(
    Long id,
    String name,
    String description,
    String ownerName,
    Long ownerId,
    int memberCount,
    long taskCount,
    LocalDateTime createdAt
) {}
