package com.taskforge.service;

import com.taskforge.domain.Task;
import com.taskforge.domain.TaskStatus;
import com.taskforge.domain.User;
import com.taskforge.dto.task.CreateTaskRequest;
import com.taskforge.dto.task.TaskResponse;
import com.taskforge.dto.task.UpdateTaskRequest;
import com.taskforge.dto.task.UpdateTaskStatusRequest;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.repository.CommentRepository;
import com.taskforge.repository.ProjectRepository;
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
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, Long creatorId) {
        var project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.projectId()));

        if (!project.isMember(creatorId)) {
            throw new AccessDeniedException("You must be a project member to create tasks");
        }

        User creator = userRepository.getReferenceById(creatorId);

        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .dueDate(request.dueDate())
                .status(TaskStatus.TODO)
                .project(project)
                .creator(creator)
                .build();

        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.assigneeId()));
            task.setAssignee(assignee);
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long userId) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        if (!task.getProject().isMember(userId)) {
            throw new AccessDeniedException("You do not have access to this task");
        }

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasksByProject(Long projectId, TaskStatus status, Long assigneeId,
                                                  Long userId, Pageable pageable) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.isMember(userId)) {
            throw new AccessDeniedException("You do not have access to this project");
        }

        return taskRepository.findByFilters(projectId, status, assigneeId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request, Long userId) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        if (!task.getProject().isMember(userId)) {
            throw new AccessDeniedException("You do not have access to this task");
        }

        if (request.title() != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueDate() != null) task.setDueDate(request.dueDate());
        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.assigneeId()));
            task.setAssignee(assignee);
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest request, Long userId) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        boolean isCreator = task.getCreator().getId().equals(userId);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(userId);
        boolean isProjectMember = task.getProject().isMember(userId);

        if (!isProjectMember) {
            throw new AccessDeniedException("You do not have access to this task");
        }
        if (!isCreator && !isAssignee) {
            throw new AccessDeniedException("Only the creator or assignee can update the task status");
        }

        task.setStatus(request.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId, boolean isAdmin) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", taskId));

        boolean isCreator = task.getCreator().getId().equals(userId);
        boolean isOwner = task.getProject().getOwner().getId().equals(userId);

        if (!isAdmin && !isCreator && !isOwner) {
            throw new AccessDeniedException("Only the task creator, project owner, or admin can delete this task");
        }

        taskRepository.delete(task);
    }

    private TaskResponse toResponse(Task task) {
        long commentCount = commentRepository.countByTaskId(task.getId());
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getProject().getId(),
                task.getProject().getName(),
                task.getCreator().getId(),
                task.getCreator().getName(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getName() : null,
                commentCount,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
