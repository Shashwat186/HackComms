package com.shashwat.hackcomms.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    List<StudentProfile> findByUserWorkspaceId(Long workspaceId);

    List<StudentProfile> findByUserIdIn(Collection<Long> userIds);

    /** A LeetCode account can be verified by only one HackComms user. */
    boolean existsByLeetcodeUsernameIgnoreCaseAndLeetcodeVerifiedTrueAndUserIdNot(String username, Long userId);

    /** Verified coders at or above the featured threshold, best first. */
    @Query("""
            select p from StudentProfile p
            where p.user.workspace.id = :workspaceId and p.leetcodeVerified = true
              and p.leetcodeRating is not null and p.leetcodeRating >= :minRating
            order by p.leetcodeRating desc
            """)
    List<StudentProfile> findFeatured(Long workspaceId, int minRating);
}
