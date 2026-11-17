package com.shashwat.hackcomms.user;

import com.shashwat.hackcomms.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    public enum Role { MEMBER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)") // plain text, so new enum values never need a migration
    private Role role = Role.MEMBER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Cached Nexconn access token; null until the user first opens chat. */
    @Column(length = 512)
    private String nexconnToken;

    /** Nexconn user ID: letters/digits only, so derive it from our numeric ID. */
    public String nexconnId() {
        return "u" + id;
    }

    public User(String email, Workspace workspace, String displayName, Role role) {
        this.email = email;
        this.workspace = workspace;
        this.displayName = displayName;
        this.role = role;
    }
}
