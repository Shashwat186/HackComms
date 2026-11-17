package com.shashwat.hackcomms.hackathon;

import com.shashwat.hackcomms.chat.Channel;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A hackathon listed in a college workspace.
 * OFFICIAL = posted (or promoted) by an admin; COMMUNITY = posted by a student and reviewed by an admin.
 * Only APPROVED hackathons are public and get a chat channel.
 */
@Entity
@Table(name = "hackathons")
@Getter
@Setter
@NoArgsConstructor
public class Hackathon {

    public enum Source { OFFICIAL, COMMUNITY }

    public enum Status { PENDING, APPROVED, REJECTED }

    public enum Mode { ONLINE, OFFLINE, HYBRID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 100)
    private String organizer;

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Mode mode = Mode.ONLINE;

    @Column(length = 120)
    private String location;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private LocalDate registrationDeadline;

    @Column(length = 60)
    private String prizePool;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Status status;

    @Column(length = 280)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posted_by")
    private User postedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
