package com.shashwat.hackcomms.profile;

import com.shashwat.hackcomms.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A student's public profile: skills, links and (optionally) a verified LeetCode account. */
@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile {

    /** Same as the user's ID. */
    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 80)
    private String headline;

    @Column(length = 500)
    private String bio;

    /** e.g. "2nd year". ("year" is a reserved word in H2/SQL, hence the column name.) */
    @Column(name = "study_year", length = 20)
    private String year;

    /** e.g. "CSE". */
    @Column(length = 40)
    private String branch;

    /** Comma-separated skill tags. */
    @Column(length = 400)
    private String skills;

    @Column(length = 200)
    private String githubUrl;

    @Column(length = 200)
    private String linkedinUrl;

    // ---------- LeetCode ----------

    @Column(length = 40)
    private String leetcodeUsername;

    /** True once the user proved ownership by putting {@link #leetcodeVerifyCode} in their LeetCode "About me". */
    private boolean leetcodeVerified;

    @Column(length = 32)
    private String leetcodeVerifyCode;

    /** Contest rating, rounded. Null if they've never done a rated contest. */
    private Integer leetcodeRating;

    /** "Knight" / "Guardian", if LeetCode awarded one. */
    @Column(length = 20)
    private String leetcodeBadge;

    private Double leetcodeTopPercent;
    private Integer leetcodeContests;
    private Integer leetcodeSolved;
    private Integer leetcodeEasy;
    private Integer leetcodeMedium;
    private Integer leetcodeHard;
    private Instant leetcodeSyncedAt;

    public StudentProfile(User user) {
        this.user = user;
    }

    public void clearLeetcode() {
        leetcodeUsername = null;
        leetcodeVerified = false;
        leetcodeVerifyCode = null;
        leetcodeRating = null;
        leetcodeBadge = null;
        leetcodeTopPercent = null;
        leetcodeContests = null;
        leetcodeSolved = null;
        leetcodeEasy = null;
        leetcodeMedium = null;
        leetcodeHard = null;
        leetcodeSyncedAt = null;
    }
}
