package com.shashwat.hackcomms.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeamPostRepository extends JpaRepository<TeamPost, Long> {

    /** Open posts on approved hackathons, plus the caller's own closed ones. Newest first. */
    @Query("""
            select p from TeamPost p
            where p.hackathon.workspace.id = :workspaceId
              and p.hackathon.status = com.shashwat.hackcomms.hackathon.Hackathon.Status.APPROVED
              and (p.open = true or p.author.id = :userId)
            order by p.createdAt desc
            """)
    List<TeamPost> findVisible(Long workspaceId, Long userId);

    boolean existsByHackathonIdAndAuthorIdAndOpenTrue(Long hackathonId, Long authorId);

    /** [hackathonId, openPostCount] pairs for a workspace. */
    @Query("select p.hackathon.id, count(p) from TeamPost p where p.hackathon.workspace.id = :workspaceId and p.open = true group by p.hackathon.id")
    List<Object[]> countOpenByWorkspace(Long workspaceId);

    @Modifying
    @Query("delete from TeamPost p where p.hackathon.id = :hackathonId")
    void deleteByHackathonId(Long hackathonId);
}
