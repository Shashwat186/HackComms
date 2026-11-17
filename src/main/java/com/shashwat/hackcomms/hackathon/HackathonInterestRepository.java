package com.shashwat.hackcomms.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface HackathonInterestRepository extends JpaRepository<HackathonInterest, Long> {

    Optional<HackathonInterest> findByHackathonIdAndUserId(Long hackathonId, Long userId);

    long countByHackathonId(Long hackathonId);

    @Query("select i.hackathon.id from HackathonInterest i where i.user.id = :userId")
    Set<Long> findHackathonIdsByUserId(Long userId);

    /** [hackathonId, count] pairs for a workspace. */
    @Query("select i.hackathon.id, count(i) from HackathonInterest i where i.hackathon.workspace.id = :workspaceId group by i.hackathon.id")
    List<Object[]> countByWorkspace(Long workspaceId);

    /** Users who starred a hackathon (for teammate-post and deadline notifications). */
    @Query("select i.user from HackathonInterest i where i.hackathon.id = :hackathonId")
    List<com.shashwat.hackcomms.user.User> findInterestedUsers(Long hackathonId);

    @Modifying
    @Query("delete from HackathonInterest i where i.hackathon.id = :hackathonId")
    void deleteByHackathonId(Long hackathonId);
}
