package com.taskforge.security;

import com.taskforge.repository.ProjectRepository;
import com.taskforge.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurityService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public boolean isOwner(Long projectId, Long userId) {
        return projectRepository.findById(projectId)
                .map(p -> p.getOwner().getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long projectId, Long userId) {
        return projectRepository.isMember(projectId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOrMember(Long projectId, Long userId) {
        return isMember(projectId, userId);  // isMember JPQL already includes owner check
    }
}
