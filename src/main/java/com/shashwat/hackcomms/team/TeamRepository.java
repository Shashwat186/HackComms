package com.shashwat.hackcomms.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Teams whose private channel the user is a member of. */
    @Query("""
            select t from Team t
            where t.channel.id in (select m.channel.id from ChannelMember m where m.user.id = :userId)
            order by t.archived asc, t.createdAt desc
            """)
    List<Team> findForMember(Long userId);

    /** Is this user already on a team for this hackathon? */
    @Query("""
            select count(t) > 0 from Team t
            where t.hackathon.id = :hackathonId
              and t.channel.id in (select m.channel.id from ChannelMember m where m.user.id = :userId)
            """)
    boolean userHasTeamFor(Long hackathonId, Long userId);

    List<Team> findByArchivedFalse();

    List<Team> findByHackathonId(Long hackathonId);
}
