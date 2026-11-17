package com.shashwat.hackcomms.notification;

import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One in-app notification for one user. */
@Entity
@Table(name = "notifications",
        indexes = @Index(columnList = "user_id, created_at"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "dedupe_key"}))
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    /** Where clicking the notification takes you. */
    public enum Link { HACKATHON, TEAM, TEAMMATES, ADMIN, PROFILE, NONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Short emoji shown as the icon, e.g. "✅". */
    @Column(length = 8)
    private String icon;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 280)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private Link link = Link.NONE;

    private Long linkId;

    /** "read" is reserved in some SQL dialects, hence the column name. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    /** Optional: prevents sending the same reminder twice (e.g. "deadline:h5"). */
    @Column(name = "dedupe_key", length = 64)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
