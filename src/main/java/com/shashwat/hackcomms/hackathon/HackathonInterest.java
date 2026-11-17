package com.shashwat.hackcomms.hackathon;

import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "I'm interested" mark on a hackathon. One per user per hackathon. */
@Entity
@Table(name = "hackathon_interests", uniqueConstraints = @UniqueConstraint(columnNames = {"hackathon_id", "user_id"}))
@Getter
@NoArgsConstructor
public class HackathonInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hackathon_id")
    private Hackathon hackathon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    public HackathonInterest(Hackathon hackathon, User user) {
        this.hackathon = hackathon;
        this.user = user;
    }
}
