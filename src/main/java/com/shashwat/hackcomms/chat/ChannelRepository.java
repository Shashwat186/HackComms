package com.shashwat.hackcomms.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceIdOrderByKindAscNameAsc(Long workspaceId);
    Optional<Channel> findByWorkspaceIdAndName(Long workspaceId, String name);
    Optional<Channel> findByNexconnId(String nexconnId);
    List<Channel> findByNexconnIdIn(Collection<String> nexconnIds);
    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);
}
