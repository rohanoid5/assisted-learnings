package com.taskforge.dto.project;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @Size(min = 2, max = 200)
    String name,

    @Size(max = 2000)
    String description
) {}
