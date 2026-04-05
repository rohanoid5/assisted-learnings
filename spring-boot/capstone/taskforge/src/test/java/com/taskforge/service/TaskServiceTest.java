package com.taskforge.service;

import com.taskforge.domain.*;
import com.taskforge.dto.task.CreateTaskRequest;
import com.taskforge.dto.task.TaskResponse;
import com.taskforge.dto.task.UpdateTaskStatusRequest;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.repository.CommentRepository;
import com.taskforge.repository.ProjectRepository;
import com.taskforge.repository.TaskRepository;
import com.taskforge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommentRepository commentRepository;

    @InjectMocks
    private TaskService taskService;

    private User owner;
    private User member;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Alice").email("alice@example.com")
                .role(Role.USER).enabled(true).build();
        member = User.builder().id(2L).name("Bob").email("bob@example.com")
                .role(Role.USER).enabled(true).build();

        project = Project.builder().id(10L).name("TaskForge").owner(owner).build();
        project.addMember(owner);
        project.addMember(member);

        task = Task.builder().id(100L).title("Fix bug").description("Critical bug")
                .status(TaskStatus.TODO).priority(Priority.HIGH)
                .project(project).creator(owner).build();
    }

    @Test
    void createTask_whenProjectMember_shouldSucceed() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Fix bug", "Critical bug", Priority.HIGH, null, 10L, null);

        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(userRepository.getReferenceById(1L)).willReturn(owner);
        given(commentRepository.countByTaskId(any())).willReturn(0L);
        given(taskRepository.save(any(Task.class))).willAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskResponse response = taskService.createTask(request, 1L);

        assertThat(response.title()).isEqualTo("Fix bug");
        assertThat(response.projectId()).isEqualTo(10L);
        then(taskRepository).should().save(any(Task.class));
    }

    @Test
    void createTask_whenNotProjectMember_shouldThrowAccessDenied() {
        User outsider = User.builder().id(99L).name("Eve").email("eve@example.com").build();
        CreateTaskRequest request = new CreateTaskRequest(
                "Fix bug", null, Priority.LOW, null, 10L, null);

        given(projectRepository.findById(10L)).willReturn(Optional.of(project));

        assertThatThrownBy(() -> taskService.createTask(request, outsider.getId()))
                .isInstanceOf(AccessDeniedException.class);

        then(taskRepository).should(never()).save(any());
    }

    @Test
    void getTaskById_whenProjectNotFound_shouldThrow() {
        given(taskRepository.findByIdWithDetails(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task not found");
    }

    @Test
    void updateTaskStatus_whenCreator_shouldSucceed() {
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS);

        given(taskRepository.findByIdWithDetails(100L)).willReturn(Optional.of(task));
        given(commentRepository.countByTaskId(100L)).willReturn(0L);
        given(taskRepository.save(any(Task.class))).willReturn(task);

        TaskResponse response = taskService.updateTaskStatus(100L, request, owner.getId());

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateTaskStatus_whenNotCreatorOrAssignee_shouldThrow() {
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(TaskStatus.DONE);

        given(taskRepository.findByIdWithDetails(100L)).willReturn(Optional.of(task));

        // member is in project but is NOT the creator or assignee
        assertThatThrownBy(() -> taskService.updateTaskStatus(100L, request, member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
