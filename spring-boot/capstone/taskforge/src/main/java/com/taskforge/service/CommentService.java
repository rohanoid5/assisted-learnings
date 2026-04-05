package com.taskforge.service;

import com.taskforge.domain.Comment;
import com.taskforge.domain.Task;
import com.taskforge.domain.User;
import com.taskforge.dto.comment.CommentResponse;
import com.taskforge.dto.comment.CreateCommentRequest;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.repository.CommentRepository;
import com.taskforge.repository.TaskRepository;
import com.taskforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse addComment(Long taskId, CreateCommentRequest request, Long authorId) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        if (!task.getProject().isMember(authorId)) {
            throw new AccessDeniedException("You must be a project member to comment on tasks");
        }

        User author = userRepository.getReferenceById(authorId);

        Comment comment = Comment.builder()
                .content(request.content())
                .task(task)
                .author(author)
                .build();

        return toResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> listComments(Long taskId, Long userId, Pageable pageable) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        if (!task.getProject().isMember(userId)) {
            throw new AccessDeniedException("You do not have access to this task");
        }

        return commentRepository.findByTaskId(taskId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteComment(Long taskId, Long commentId, Long userId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));

        if (!comment.getTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Comment", "id", commentId);
        }

        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        if (!isAdmin && !isAuthor) {
            throw new AccessDeniedException("Only the comment author or an admin can delete this comment");
        }

        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
