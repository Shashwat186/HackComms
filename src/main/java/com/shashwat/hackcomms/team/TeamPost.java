package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.hackathon.Hackathon;
import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A teammate-finding post on a hackathon. */
@Entity
@Table(name = "team_posts", indexes = @Index(columnList = "hackathon_id"))
@Getter
@Setter
@NoArgsConstructor
public class TeamPost {

    public enum Type {
        /** "I'm a solo student, looking to join a team." */
        LOOKING_FOR_TEAM,
        /** "Our team has open spots." */
        LOOKING_FOR_MEMBERS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hackathon_id")
    private Hackathon hackathon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Type type;

    /** Comma-separated skill tags, e.g. "React,Figma,ML". */
    @Column(length = 300)
    private String skills;

    @Column(length = 500)
    private String message;

    /** Open spots (team posts only). */
    private Integer spotsOpen;

    /** False once the author marks it filled. */
    private boolean open = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
