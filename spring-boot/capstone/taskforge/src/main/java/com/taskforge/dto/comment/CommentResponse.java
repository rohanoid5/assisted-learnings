package com.taskforge.dto.comment;

import java.time.LocalDateTime;

public record CommentResponse(
    Long id,
    String content,
    Long authorId,
    String authorName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
