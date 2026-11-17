package com.shashwat.hackcomms.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByDomain(String domain);
    boolean existsBySlug(String slug);
}
