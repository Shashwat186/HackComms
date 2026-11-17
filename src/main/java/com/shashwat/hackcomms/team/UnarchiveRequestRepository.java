package com.shashwat.hackcomms.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UnarchiveRequestRepository extends JpaRepository<UnarchiveRequest, Long> {

    Optional<UnarchiveRequest> findFirstByTeamIdAndStatus(Long teamId, UnarchiveRequest.Status status);

    @Query("""
            select r from UnarchiveRequest r
            where r.team.workspace.id = :workspaceId
            order by case when r.status = com.shashwat.hackcomms.team.UnarchiveRequest.Status.PENDING then 0 else 1 end,
                     r.createdAt desc
            """)
    List<UnarchiveRequest> findForWorkspace(Long workspaceId);

    @Modifying
    @Query("delete from UnarchiveRequest r where r.team.id in (select t.id from Team t where t.hackathon.id = :hackathonId)")
    void deleteByHackathonId(Long hackathonId);
}
