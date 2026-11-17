package com.shashwat.hackcomms.workspace;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One college = one workspace. Identified by the college email domain
 * (e.g. "jiit.ac.in"); students prove membership by receiving an OTP at that domain.
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised college domain, e.g. "jiit.ac.in". Unique. */
    @Column(nullable = false, unique = true)
    private String domain;

    /** Short URL-friendly key, e.g. "jiit". Unique. */
    @Column(nullable = false, unique = true)
    private String slug;

    /** Display name, e.g. "JIIT". Admins can rename it later. */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Workspace(String domain, String slug, String name) {
        this.domain = domain;
        this.slug = slug;
        this.name = name;
    }
}
