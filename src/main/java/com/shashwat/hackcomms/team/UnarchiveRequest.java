package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A team member asking the workspace admin to reopen an archived team channel. */
@Entity
@Table(name = "unarchive_requests")
@Getter
@Setter
@NoArgsConstructor
public class UnarchiveRequest {

    public enum Status { PENDING, APPROVED, DENIED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(length = 280)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(length = 280)
    private String reviewNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant reviewedAt;
}
