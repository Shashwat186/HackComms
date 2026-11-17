package com.shashwat.hackcomms.chat;

import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A topic channel inside a workspace (#general, #hackathon-sih-2026, ...).
 * Backed 1:1 by a Nexconn group channel whose ID is {@link #getNexconnId()}.
 */
@Entity
@Table(name = "channels", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class Channel {

    /** TEAM channels are private: never listed for non-members, can't be self-joined. */
    public enum Kind { GENERAL, TOPIC, HACKATHON, TEAM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    /** Lowercase slug, e.g. "general", "sih-2026". Unique per workspace. */
    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 280)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Kind kind = Kind.TOPIC;

    /** Nexconn group channel ID (letters/digits only), e.g. "ws1c3". Set right after insert. */
    @Column(unique = true, length = 64)
    private String nexconnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Channel(Workspace workspace, String name, String description, Kind kind, User createdBy) {
        this.workspace = workspace;
        this.name = name;
        this.description = description;
        this.kind = kind;
        this.createdBy = createdBy;
    }
}
