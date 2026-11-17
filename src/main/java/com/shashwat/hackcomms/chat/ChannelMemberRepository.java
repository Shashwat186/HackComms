package com.shashwat.hackcomms.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {

    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    long countByChannelId(Long channelId);

    List<ChannelMember> findByChannelId(Long channelId);

    List<ChannelMember> findByChannelIdOrderByJoinedAtAsc(Long channelId);

    void deleteByChannelIdAndUserId(Long channelId, Long userId);

    @Query("select m.channel.id from ChannelMember m where m.user.id = :userId")
    Set<Long> findChannelIdsByUserId(Long userId);

    /** [channelId, memberCount] pairs for a workspace. */
    @Query("select m.channel.id, count(m) from ChannelMember m where m.channel.workspace.id = :workspaceId group by m.channel.id")
    List<Object[]> countMembersByWorkspace(Long workspaceId);
}
