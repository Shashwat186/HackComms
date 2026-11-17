package com.shashwat.hackcomms.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HackathonRepository extends JpaRepository<Hackathon, Long> {

    /** Public list: everything approved, plus the caller's own pending/rejected posts. */
    @Query("""
            select h from Hackathon h
            where h.workspace.id = :workspaceId
              and (h.status = com.shashwat.hackcomms.hackathon.Hackathon.Status.APPROVED or h.postedBy.id = :userId)
            order by h.startDate asc
            """)
    List<Hackathon> findVisible(Long workspaceId, Long userId);

    List<Hackathon> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    /** For deadline reminders. */
    List<Hackathon> findByStatusAndRegistrationDeadlineBetween(Hackathon.Status status, java.time.LocalDate from,
                                                              java.time.LocalDate to);

    /** Hackathons that own a channel, used to group channels in the sidebar. */
    List<Hackathon> findByWorkspaceIdAndChannelIsNotNull(Long workspaceId);
}
