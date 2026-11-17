package com.shashwat.hackcomms.profile;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.profile.LeetCodeClient.LeetCodeUnavailableException;
import com.shashwat.hackcomms.profile.LeetCodeClient.Stats;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Pattern LC_USERNAME = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern LC_URL = Pattern.compile("leetcode\\.com/(?:u/)?([A-Za-z0-9_-]+)/?", Pattern.CASE_INSENSITIVE);
    private static final Duration AUTO_REFRESH_AFTER = Duration.ofHours(12);
    private static final Duration MIN_REFRESH_GAP = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SKILLS = 12;

    private final StudentProfileRepository profiles;
    private final UserRepository users;
    private final LeetCodeClient leetcode;

    @Value("${hackcomms.featured.min-rating:1600}")
    private int featuredMinRating;

    public record ProfileUpdate(String displayName, String headline, String bio, String year, String branch,
                                List<String> skills, String githubUrl, String linkedinUrl) {}

    public record LeetCodeLink(String username) {}

    public record LeetCodeDto(String username, String profileUrl, boolean verified, String verifyCode,
                              Integer rating, String badge, Double topPercent, Integer contests,
                              Integer solved, Integer easy, Integer medium, Integer hard, Instant syncedAt) {}

    public record ProfileDto(Long userId, String nexconnId, String displayName, String email, String role,
                             String headline, String bio, String year, String branch, List<String> skills,
                             String githubUrl, String linkedinUrl, LeetCodeDto leetcode, boolean featured,
                             int featuredMinRating, boolean mine) {}

    /** Compact card for directories, sidebars and featured lists. Rating only shown if verified. */
    public record PersonSummary(Long userId, String nexconnId, String displayName, String email, String headline,
                                String year, String branch, List<String> skills, Integer leetcodeRating,
                                String leetcodeBadge, boolean featured) {}

    // ---------- read ----------

    @Transactional
    public ProfileDto get(AuthUser me, Long userId) {
        User user = users.findById(userId)
                .filter(u -> u.getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        StudentProfile p = profileOf(user);
        autoRefresh(p);
        return toDto(me, p);
    }

    @Transactional(readOnly = true)
    public List<PersonSummary> directory(AuthUser me) {
        Map<Long, StudentProfile> byUser = profiles.findByUserWorkspaceId(me.workspaceId()).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));
        return users.findByWorkspaceIdOrderByDisplayNameAsc(me.workspaceId()).stream()
                .map(u -> summary(u, byUser.get(u.getId())))
                .sorted(Comparator.comparing(PersonSummary::featured).reversed()
                        .thenComparing(s -> -Optional.ofNullable(s.leetcodeRating()).orElse(0))
                        .thenComparing(PersonSummary::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PersonSummary> featured(AuthUser me) {
        return profiles.findFeatured(me.workspaceId(), featuredMinRating).stream()
                .limit(12)
                .map(p -> summary(p.getUser(), p))
                .toList();
    }

    /** Summaries for a set of users (used to decorate team posts with ratings). */
    @Transactional(readOnly = true)
    public Map<Long, PersonSummary> summaries(Collection<User> forUsers) {
        Map<Long, StudentProfile> byUser = profiles.findByUserIdIn(forUsers.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));
        Map<Long, PersonSummary> out = new HashMap<>();
        for (User u : forUsers) out.put(u.getId(), summary(u, byUser.get(u.getId())));
        return out;
    }

    // ---------- write (own profile) ----------

    @Transactional
    public ProfileDto update(AuthUser me, ProfileUpdate req) {
        if (req == null) throw bad("Nothing to update.");
        User user = load(me);
        String name = clean(req.displayName(), 60);
        if (name == null || name.length() < 2) throw bad("Your name needs at least 2 characters.");
        user.setDisplayName(name);

        StudentProfile p = profileOf(user);
        p.setHeadline(clean(req.headline(), 80));
        p.setBio(clean(req.bio(), 500));
        p.setYear(clean(req.year(), 20));
        p.setBranch(clean(req.branch(), 40));
        List<String> skills = normaliseSkills(req.skills());
        p.setSkills(skills.isEmpty() ? null : String.join(",", skills));
        p.setGithubUrl(link(req.githubUrl(), "github.com", "GitHub"));
        p.setLinkedinUrl(link(req.linkedinUrl(), "linkedin.com", "LinkedIn"));
        return toDto(me, profiles.save(p));
    }

    /** Step 1: remember the username and hand out a code to paste into LeetCode's "About me". */
    @Transactional
    public ProfileDto linkLeetcode(AuthUser me, String raw) {
        String username = parseUsername(raw);
        StudentProfile p = profileOf(load(me));
        if (!username.equalsIgnoreCase(Optional.ofNullable(p.getLeetcodeUsername()).orElse("")) || !p.isLeetcodeVerified()) {
            p.clearLeetcode();
            p.setLeetcodeUsername(username);
            p.setLeetcodeVerifyCode("hackcomms-" + Long.toString(Math.abs(RANDOM.nextLong()), 36).substring(0, 6));
        }
        return toDto(me, profiles.save(p));
    }

    /** Step 2: check the code is in their LeetCode "About me", then pull stats. */
    @Transactional
    public ProfileDto verifyLeetcode(AuthUser me) {
        StudentProfile p = profileOf(load(me));
        if (p.getLeetcodeUsername() == null || p.getLeetcodeVerifyCode() == null) {
            throw bad("Add your LeetCode username first.");
        }
        if (profiles.existsByLeetcodeUsernameIgnoreCaseAndLeetcodeVerifiedTrueAndUserIdNot(p.getLeetcodeUsername(), p.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That LeetCode account is already verified by another student.");
        }
        Stats s = fetchOrFail(p.getLeetcodeUsername());
        if (!s.aboutMe().contains(p.getLeetcodeVerifyCode())) {
            throw bad("Couldn't find " + p.getLeetcodeVerifyCode() + " in your LeetCode \"About me\" (Summary) yet. "
                    + "Save it on LeetCode, wait a few seconds and try again.");
        }
        p.setLeetcodeVerified(true);
        apply(p, s);
        return toDto(me, p);
    }

    @Transactional
    public ProfileDto refreshLeetcode(AuthUser me) {
        StudentProfile p = profileOf(load(me));
        if (!p.isLeetcodeVerified()) throw bad("Verify your LeetCode account first.");
        if (p.getLeetcodeSyncedAt() != null && p.getLeetcodeSyncedAt().plus(MIN_REFRESH_GAP).isAfter(Instant.now())) {
            return toDto(me, p); // just synced; nothing new
        }
        apply(p, fetchOrFail(p.getLeetcodeUsername()));
        return toDto(me, p);
    }

    @Transactional
    public ProfileDto unlinkLeetcode(AuthUser me) {
        StudentProfile p = profileOf(load(me));
        p.clearLeetcode();
        return toDto(me, p);
    }

    // ---------- internals ----------

    private void autoRefresh(StudentProfile p) {
        if (!p.isLeetcodeVerified()) return;
        Instant synced = p.getLeetcodeSyncedAt();
        if (synced != null && synced.plus(AUTO_REFRESH_AFTER).isAfter(Instant.now())) return;
        try {
            leetcode.fetch(p.getLeetcodeUsername()).ifPresent(s -> apply(p, s));
        } catch (LeetCodeUnavailableException e) {
            log.debug("Skipping LeetCode auto-refresh: {}", e.getMessage());
        }
    }

    private Stats fetchOrFail(String username) {
        try {
            return leetcode.fetch(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No LeetCode user called \"" + username + "\". Check the spelling."));
        } catch (LeetCodeUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private static void apply(StudentProfile p, Stats s) {
        p.setLeetcodeUsername(s.username());
        p.setLeetcodeRating(s.rating());
        p.setLeetcodeBadge(s.badge());
        p.setLeetcodeTopPercent(s.topPercent());
        p.setLeetcodeContests(s.contests());
        p.setLeetcodeSolved(s.solved());
        p.setLeetcodeEasy(s.easy());
        p.setLeetcodeMedium(s.medium());
        p.setLeetcodeHard(s.hard());
        p.setLeetcodeSyncedAt(Instant.now());
    }

    private StudentProfile profileOf(User user) {
        return profiles.findById(user.getId()).orElseGet(() -> profiles.save(new StudentProfile(user)));
    }

    private boolean isFeatured(StudentProfile p) {
        return p != null && p.isLeetcodeVerified() && p.getLeetcodeRating() != null
                && p.getLeetcodeRating() >= featuredMinRating;
    }

    private PersonSummary summary(User u, StudentProfile p) {
        boolean verified = p != null && p.isLeetcodeVerified();
        return new PersonSummary(u.getId(), u.nexconnId(), u.getDisplayName(), u.getEmail(),
                p == null ? null : p.getHeadline(), p == null ? null : p.getYear(), p == null ? null : p.getBranch(),
                p == null ? List.of() : splitSkills(p.getSkills()),
                verified ? p.getLeetcodeRating() : null, verified ? p.getLeetcodeBadge() : null, isFeatured(p));
    }

    private ProfileDto toDto(AuthUser me, StudentProfile p) {
        User u = p.getUser();
        boolean mine = u.getId().equals(me.userId());
        LeetCodeDto lc = null;
        // Others only see a LeetCode section once it's verified.
        if (p.getLeetcodeUsername() != null && (mine || p.isLeetcodeVerified())) {
            lc = new LeetCodeDto(p.getLeetcodeUsername(), "https://leetcode.com/u/" + p.getLeetcodeUsername() + "/",
                    p.isLeetcodeVerified(), mine && !p.isLeetcodeVerified() ? p.getLeetcodeVerifyCode() : null,
                    p.getLeetcodeRating(), p.getLeetcodeBadge(), p.getLeetcodeTopPercent(), p.getLeetcodeContests(),
                    p.getLeetcodeSolved(), p.getLeetcodeEasy(), p.getLeetcodeMedium(), p.getLeetcodeHard(),
                    p.getLeetcodeSyncedAt());
        }
        return new ProfileDto(u.getId(), u.nexconnId(), u.getDisplayName(), u.getEmail(), u.getRole().name(),
                p.getHeadline(), p.getBio(), p.getYear(), p.getBranch(), splitSkills(p.getSkills()),
                p.getGithubUrl(), p.getLinkedinUrl(), lc, isFeatured(p), featuredMinRating, mine);
    }

    /** Accepts "name", "@name" or a pasted profile URL. */
    static String parseUsername(String raw) {
        String s = raw == null ? "" : raw.trim();
        Matcher m = LC_URL.matcher(s);
        if (m.find()) s = m.group(1);
        s = s.replaceFirst("^@", "");
        if (!LC_USERNAME.matcher(s).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That doesn't look like a LeetCode username.");
        }
        return s;
    }

    private static List<String> normaliseSkills(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.replace(",", " ").trim().replaceAll("\\s+", " ");
            if (t.isEmpty()) continue;
            if (t.length() > 24) t = t.substring(0, 24);
            seen.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
            if (seen.size() == MAX_SKILLS) break;
        }
        return new ArrayList<>(seen.values());
    }

    private static List<String> splitSkills(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    private static String link(String raw, String host, String label) {
        String s = clean(raw, 200);
        if (s == null) return null;
        if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
        if (!s.toLowerCase(Locale.ROOT).matches("^https?://([a-z0-9-]+\\.)*" + Pattern.quote(host) + "(/.*)?$")) {
            throw bad("That doesn't look like a " + label + " link.");
        }
        return s;
    }

    private User load(AuthUser me) {
        return users.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
