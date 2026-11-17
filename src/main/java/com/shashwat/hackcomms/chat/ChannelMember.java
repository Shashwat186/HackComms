package com.shashwat.hackcomms.chat;

import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Local record of channel membership (mirrors the Nexconn group membership). */
@Entity
@Table(name = "channel_members", uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "user_id"}))
@Getter
@NoArgsConstructor
public class ChannelMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public ChannelMember(Channel channel, User user) {
        this.channel = channel;
        this.user = user;
    }
}
