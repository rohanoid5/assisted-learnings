package com.taskforge.repository;

import com.taskforge.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerId(Long ownerId);

    @Query("""
        SELECT p FROM Project p
        JOIN p.members m
        WHERE m.id = :userId
        """)
    List<Project> findByMemberId(@Param("userId") Long userId);

    @Query("""
        SELECT p FROM Project p
        WHERE p.owner.id = :userId
           OR EXISTS (SELECT 1 FROM p.members m WHERE m.id = :userId)
        """)
    List<Project> findAllAccessibleByUser(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(m) > 0 FROM Project p JOIN p.members m
        WHERE p.id = :projectId AND m.id = :userId
        """)
    boolean isMember(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query("""
        SELECT p FROM Project p
        LEFT JOIN FETCH p.owner
        LEFT JOIN FETCH p.members
        WHERE p.id = :id
        """)
    Optional<Project> findByIdWithMembers(@Param("id") Long id);
}
