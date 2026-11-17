package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.hackathon.Hackathon;
import com.shashwat.hackcomms.hackathon.HackathonInterestRepository;
import com.shashwat.hackcomms.hackathon.HackathonRepository;
import com.shashwat.hackcomms.notification.Notification.Link;
import com.shashwat.hackcomms.notification.NotificationService;
import com.shashwat.hackcomms.profile.ProfileService;
import com.shashwat.hackcomms.profile.ProfileService.PersonSummary;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamPostService {

    private static final int MAX_SKILLS = 8;
    private static final int MAX_SKILL_LENGTH = 24;

    private final TeamPostRepository posts;
    private final HackathonRepository hackathons;
    private final UserRepository users;
    private final ProfileService profiles;
    private final HackathonInterestRepository interests;
    private final NotificationService notifications;

    public record TeamPostRequest(Long hackathonId, TeamPost.Type type, List<String> skills, String message,
                                  Integer spotsOpen) {}

    public record Author(Long id, String name, String nexconnId, String email, Integer leetcodeRating,
                         boolean featured) {}

    public record HackathonRef(Long id, String title, String source, LocalDate startDate) {}

    public record TeamPostDto(Long id, String type, List<String> skills, String message, Integer spotsOpen,
                              boolean open, Author author, HackathonRef hackathon, boolean mine, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<TeamPostDto> list(AuthUser me) {
        List<TeamPost> visible = posts.findVisible(me.workspaceId(), me.userId());
        Map<Long, PersonSummary> people = profiles.summaries(visible.stream().map(TeamPost::getAuthor).distinct().toList());
        return visible.stream().map(p -> toDto(me, p, people.get(p.getAuthor().getId()))).toList();
    }

    @Transactional
    public TeamPostDto create(AuthUser me, TeamPostRequest req) {
        if (req == null || req.hackathonId() == null) throw bad("Pick a hackathon.");
        if (req.type() == null) throw bad("Say whether you need a team or your team needs people.");
        User author = users.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
        Hackathon h = hackathons.findById(req.hackathonId())
                .filter(x -> x.getWorkspace().getId().equals(me.workspaceId()))
                .filter(x -> x.getStatus() == Hackathon.Status.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hackathon not found"));
        if (h.getEndDate().isBefore(LocalDate.now())) throw bad("This hackathon has already ended.");
        if (posts.existsByHackathonIdAndAuthorIdAndOpenTrue(h.getId(), author.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open post for this hackathon. Mark it filled or delete it first.");
        }

        List<String> skills = normaliseSkills(req.skills());
        String message = req.message() == null ? null : req.message().trim();
        if (skills.isEmpty() && (message == null || message.isEmpty())) {
            throw bad("Add at least one skill or a short note so people know what you're after.");
        }

        TeamPost p = new TeamPost();
        p.setHackathon(h);
        p.setAuthor(author);
        p.setType(req.type());
        p.setSkills(skills.isEmpty() ? null : String.join(",", skills));
        p.setMessage(message == null || message.isEmpty() ? null : truncate(message, 500));
        if (req.type() == TeamPost.Type.LOOKING_FOR_MEMBERS) {
            int spots = req.spotsOpen() == null ? 1 : req.spotsOpen();
            if (spots < 1 || spots > 10) throw bad("Open spots should be between 1 and 10.");
            p.setSpotsOpen(spots);
        }
        posts.save(p);
        // Let students who starred this hackathon know someone's looking.
        String what = req.type() == TeamPost.Type.LOOKING_FOR_MEMBERS
                ? author.getDisplayName() + "'s team needs " + p.getSpotsOpen() + " more"
                : author.getDisplayName() + " is looking for a team";
        notifications.notifyAll(interests.findInterestedUsers(h.getId()), author, "🤝",
                "New teammate post for " + h.getTitle(),
                what + (skills.isEmpty() ? "" : " · " + String.join(", ", skills)), Link.TEAMMATES, h.getId());
        return toDto(me, p);
    }

    /** Author marks it filled (or reopens it). */
    @Transactional
    public TeamPostDto setOpen(AuthUser me, Long id, boolean open) {
        TeamPost p = ownedOrAdmin(me, id);
        if (open && posts.existsByHackathonIdAndAuthorIdAndOpenTrue(p.getHackathon().getId(), p.getAuthor().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have another open post for this hackathon.");
        }
        p.setOpen(open);
        return toDto(me, p);
    }

    @Transactional
    public void delete(AuthUser me, Long id) {
        posts.delete(ownedOrAdmin(me, id));
    }

    // ---------- internals ----------

    private TeamPost ownedOrAdmin(AuthUser me, Long id) {
        TeamPost p = posts.findById(id)
                .filter(x -> x.getHackathon().getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        boolean admin = users.findById(me.userId()).map(u -> u.getRole() == User.Role.ADMIN).orElse(false);
        if (!p.getAuthor().getId().equals(me.userId()) && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can change this post.");
        }
        return p;
    }

    /** Trims, drops blanks, de-duplicates case-insensitively, caps count and length. */
    static List<String> normaliseSkills(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String s : raw) {
            if (s == null) continue;
            String t = s.replace(",", " ").trim().replaceAll("\\s+", " ");
            if (t.isEmpty()) continue;
            t = truncate(t, MAX_SKILL_LENGTH);
            seen.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
            if (seen.size() == MAX_SKILLS) break;
        }
        return new ArrayList<>(seen.values());
    }

    private TeamPostDto toDto(AuthUser me, TeamPost p) {
        return toDto(me, p, profiles.summaries(List.of(p.getAuthor())).get(p.getAuthor().getId()));
    }

    private TeamPostDto toDto(AuthUser me, TeamPost p, PersonSummary s) {
        User a = p.getAuthor();
        Hackathon h = p.getHackathon();
        List<String> skills = p.getSkills() == null ? List.of() : List.of(p.getSkills().split(","));
        return new TeamPostDto(p.getId(), p.getType().name(), skills, p.getMessage(), p.getSpotsOpen(), p.isOpen(),
                new Author(a.getId(), a.getDisplayName(), a.nexconnId(), a.getEmail(),
                        s == null ? null : s.leetcodeRating(), s != null && s.featured()),
                new HackathonRef(h.getId(), h.getTitle(), h.getSource().name(), h.getStartDate()),
                a.getId().equals(me.userId()), p.getCreatedAt());
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
