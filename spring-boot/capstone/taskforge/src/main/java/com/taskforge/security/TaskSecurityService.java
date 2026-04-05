package com.taskforge.security;

import com.taskforge.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("taskSecurity")
@RequiredArgsConstructor
public class TaskSecurityService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public boolean isCreator(Long taskId, Long userId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getCreator().getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAssignee(Long taskId, Long userId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getAssignee() != null && t.getAssignee().getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isCreatorOrAssignee(Long taskId, Long userId) {
        return taskRepository.findById(taskId)
                .map(t -> t.getCreator().getId().equals(userId)
                        || (t.getAssignee() != null && t.getAssignee().getId().equals(userId)))
                .orElse(false);
    }
}
