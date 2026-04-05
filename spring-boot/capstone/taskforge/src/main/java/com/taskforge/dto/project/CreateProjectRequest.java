package com.taskforge.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlank(message = "Project name is required")
    @Size(min = 2, max = 200)
    String name,

    @Size(max = 2000)
    String description,

    Long ownerId   // optional — defaults to current user if null
) {}
