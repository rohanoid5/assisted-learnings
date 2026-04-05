package com.taskforge.service;

import com.taskforge.domain.Project;
import com.taskforge.domain.User;
import com.taskforge.dto.project.CreateProjectRequest;
import com.taskforge.dto.project.ProjectResponse;
import com.taskforge.dto.project.UpdateProjectRequest;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.repository.ProjectRepository;
import com.taskforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .owner(owner)
                .build();

        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long projectId, Long userId) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.isMember(userId)) {
            throw new AccessDeniedException("You do not have access to this project");
        }

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listUserProjects(Long userId) {
        return projectRepository.findAllAccessibleByUser(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest request, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Only the project owner can update the project");
        }

        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void addMember(Long projectId, Long newMemberId, Long requesterId) {
        Project project = projectRepository.findByIdWithMembers(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!project.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only the project owner can add members");
        }

        User newMember = userRepository.findById(newMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", newMemberId));

        project.addMember(newMember);
        projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId, boolean isAdmin) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!isAdmin && !project.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Only the project owner or an admin can delete the project");
        }

        projectRepository.delete(project);
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getName(),
                project.getOwner().getId(),
                project.getMembers().size(),
                project.getTasks().size(),
                project.getCreatedAt()
        );
    }
}
