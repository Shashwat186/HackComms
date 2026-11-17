package com.shashwat.hackcomms.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaces;

    /** Returns the college's workspace, creating it the first time a student from that domain logs in. */
    @Transactional
    public Workspace findOrCreate(CollegeDomain college) {
        return workspaces.findByDomain(college.domain())
                .orElseGet(() -> workspaces.save(
                        new Workspace(college.domain(), uniqueSlug(college.slug()), college.displayName())));
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int n = 2;
        while (workspaces.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }
}
