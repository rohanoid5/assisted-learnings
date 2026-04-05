package com.taskforge.controller;

import com.taskforge.dto.ApiResponse;
import com.taskforge.dto.project.CreateProjectRequest;
import com.taskforge.dto.project.ProjectResponse;
import com.taskforge.dto.project.UpdateProjectRequest;
import com.taskforge.security.UserPrincipal;
import com.taskforge.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ProjectResponse> projects = projectService.listUserProjects(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectResponse project = projectService.getProjectById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(project));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectResponse project = projectService.createProject(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(project, "Project created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@projectSecurity.isOwner(#id, authentication.principal.id)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectResponse project = projectService.updateProject(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(project, "Project updated"));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@projectSecurity.isOwner(#id, authentication.principal.id)")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long memberId = body.get("userId");
        if (memberId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("userId is required"));
        }
        projectService.addMember(id, memberId, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Member added successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        projectService.deleteProject(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project deleted"));
    }
}
