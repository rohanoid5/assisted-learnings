package com.taskforge.repository;

import com.taskforge.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TaskRepository taskRepository;

    private User savedUser(String name, String email) {
        User user = User.builder()
                .name(name).email(email)
                .passwordHash("{noop}password")
                .role(Role.USER).enabled(true)
                .build();
        return em.persistAndFlush(user);
    }

    private Project savedProject(User owner) {
        Project project = Project.builder()
                .name("Test Project")
                .owner(owner)
                .build();
        project.addMember(owner);
        return em.persistAndFlush(project);
    }

    private Task savedTask(Project project, User creator, TaskStatus status) {
        Task task = Task.builder()
                .title("Task " + status)
                .status(status)
                .priority(Priority.MEDIUM)
                .project(project)
                .creator(creator)
                .build();
        return em.persistAndFlush(task);
    }

    @Test
    void findByProjectId_shouldReturnPagedTasks() {
        User alice = savedUser("Alice", "alice@test.com");
        Project project = savedProject(alice);
        savedTask(project, alice, TaskStatus.TODO);
        savedTask(project, alice, TaskStatus.IN_PROGRESS);
        em.clear();

        Page<Task> result = taskRepository.findByProjectId(project.getId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByProjectIdAndStatus_shouldFilterByStatus() {
        User alice = savedUser("Alice2", "alice2@test.com");
        Project project = savedProject(alice);
        savedTask(project, alice, TaskStatus.TODO);
        savedTask(project, alice, TaskStatus.DONE);
        em.clear();

        Page<Task> result = taskRepository.findByProjectIdAndStatus(
                project.getId(), TaskStatus.TODO, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void countByProjectId_shouldReturnCorrectCount() {
        User alice = savedUser("Alice3", "alice3@test.com");
        Project project = savedProject(alice);
        savedTask(project, alice, TaskStatus.TODO);
        savedTask(project, alice, TaskStatus.IN_PROGRESS);
        savedTask(project, alice, TaskStatus.DONE);
        em.clear();

        long count = taskRepository.countByProjectId(project.getId());

        assertThat(count).isEqualTo(3);
    }

    @Test
    void findByFilters_withNullFilters_shouldReturnAllProjectTasks() {
        User alice = savedUser("Alice4", "alice4@test.com");
        Project project = savedProject(alice);
        savedTask(project, alice, TaskStatus.TODO);
        savedTask(project, alice, TaskStatus.REVIEW);
        em.clear();

        Page<Task> result = taskRepository.findByFilters(
                project.getId(), null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
