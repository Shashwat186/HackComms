package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.chat.Channel;
import com.shashwat.hackcomms.hackathon.Hackathon;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A hackathon team with a private Nexconn group channel. Membership = channel membership.
 * Becomes a read-only archive after the hackathon (end date + grace days), unless an admin extends it.
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hackathon_id")
    private Hackathon hackathon;

    @Column(nullable = false, length = 40)
    private String name;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id")
    private User leader;

    @Column(nullable = false)
    private int maxSize = 4;

    private boolean archived;

    private Instant archivedAt;

    /** Set when an admin unarchives: don't auto-archive again before this date. */
    private LocalDate keepActiveUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
