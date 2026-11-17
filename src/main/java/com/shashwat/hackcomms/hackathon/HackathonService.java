package com.shashwat.hackcomms.hackathon;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.chat.Channel;
import com.shashwat.hackcomms.chat.ChatService;
import com.shashwat.hackcomms.team.TeamPostRepository;
import com.shashwat.hackcomms.team.TeamRepository;
import com.shashwat.hackcomms.notification.Notification.Link;
import com.shashwat.hackcomms.notification.NotificationService;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HackathonService {

    private final HackathonRepository hackathons;
    private final HackathonInterestRepository interests;
    private final UserRepository users;
    private final ChatService chat;
    private final TeamPostRepository teamPosts;
    private final TeamRepository teamRepo;
    private final NotificationService notifications;

    public record HackathonRequest(String title, String organizer, String description, String websiteUrl,
                                   Hackathon.Mode mode, String location, LocalDate startDate, LocalDate endDate,
                                   LocalDate registrationDeadline, String prizePool) {}

    public record PersonRef(Long id, String name) {}

    public record ChannelRef(Long id, String name, String nexconnId) {}

    public record HackathonDto(Long id, String title, String organizer, String description, String websiteUrl,
                               String mode, String location, LocalDate startDate, LocalDate endDate,
                               LocalDate registrationDeadline, String prizePool, String source, String status,
                               String rejectionReason, PersonRef postedBy, ChannelRef channel,
                               long interestedCount, boolean interested, boolean mine, Instant createdAt,
                               long openTeamPosts) {}

    // ---------- student + admin ----------

    @Transactional(readOnly = true)
    public List<HackathonDto> listVisible(AuthUser me) {
        return toDtos(me, hackathons.findVisible(me.workspaceId(), me.userId()));
    }

    /** Admins publish straight away as OFFICIAL; students' posts wait for review as COMMUNITY. */
    @Transactional
    public HackathonDto create(AuthUser me, HackathonRequest req) {
        User user = load(me);
        Hackathon h = new Hackathon();
        h.setWorkspace(user.getWorkspace());
        h.setPostedBy(user);
        apply(h, req);
        if (isAdmin(user)) {
            h.setSource(Hackathon.Source.OFFICIAL);
            h.setStatus(Hackathon.Status.APPROVED);
            h.setReviewedBy(user);
            hackathons.save(h);
            openChannel(h);
            notifications.notifyWorkspace(me.workspaceId(), user, "🏆", "New official hackathon: " + h.getTitle(),
                    whenText(h), Link.HACKATHON, h.getId());
        } else {
            h.setSource(Hackathon.Source.COMMUNITY);
            h.setStatus(Hackathon.Status.PENDING);
            hackathons.save(h);
            notifications.notifyAdmins(me.workspaceId(), user, "📝", "New hackathon to review",
                    user.getDisplayName() + " posted “" + h.getTitle() + "”.", Link.ADMIN, null);
        }
        return toDto(me, h);
    }

    @Transactional
    public HackathonDto toggleInterest(AuthUser me, Long id) {
        Hackathon h = find(me, id);
        if (h.getStatus() != Hackathon.Status.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This hackathon isn't published yet.");
        }
        interests.findByHackathonIdAndUserId(id, me.userId()).ifPresentOrElse(
                interests::delete,
                () -> interests.save(new HackathonInterest(h, load(me))));
        interests.flush();
        return toDto(me, h);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public List<HackathonDto> listAll(AuthUser me) {
        requireAdmin(me);
        return toDtos(me, hackathons.findByWorkspaceIdOrderByCreatedAtDesc(me.workspaceId()));
    }

    /** Approve a pending post (optionally as OFFICIAL), or promote an approved community post to OFFICIAL. */
    @Transactional
    public HackathonDto approve(AuthUser me, Long id, boolean official) {
        User admin = requireAdmin(me);
        Hackathon h = find(me, id);
        boolean wasLive = h.getStatus() == Hackathon.Status.APPROVED;
        boolean wasOfficial = h.getSource() == Hackathon.Source.OFFICIAL;
        if (official) h.setSource(Hackathon.Source.OFFICIAL);
        h.setStatus(Hackathon.Status.APPROVED);
        h.setRejectionReason(null);
        h.setReviewedBy(admin);
        openChannel(h);

        User poster = h.getPostedBy();
        boolean posterIsAdmin = poster.getId().equals(admin.getId());
        if (!wasLive) {
            if (!posterIsAdmin) {
                notifications.notify(poster, "✅", "Your hackathon “" + h.getTitle() + "” is live",
                        (official ? "An admin approved it as Official. " : "An admin approved it. ")
                                + "#" + h.getChannel().getName() + " is ready.", Link.HACKATHON, h.getId());
            }
            // Tell everyone else about the new listing.
            notifications.notifyAll(users.findByWorkspaceIdOrderByDisplayNameAsc(me.workspaceId()).stream()
                            .filter(u -> !u.getId().equals(poster.getId())).toList(), admin,
                    "🏆", "New hackathon: " + h.getTitle(), whenText(h), Link.HACKATHON, h.getId());
        } else if (official && !wasOfficial && !posterIsAdmin) {
            notifications.notify(poster, "⭐", "“" + h.getTitle() + "” is now an Official hackathon",
                    "An admin verified your post.", Link.HACKATHON, h.getId());
        }
        return toDto(me, h);
    }

    @Transactional
    public HackathonDto reject(AuthUser me, Long id, String reason) {
        User admin = requireAdmin(me);
        Hackathon h = find(me, id);
        if (h.getChannel() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This hackathon is already live with a channel. Delete it instead.");
        }
        h.setStatus(Hackathon.Status.REJECTED);
        h.setRejectionReason(blankToNull(reason, 280));
        h.setReviewedBy(admin);
        if (!h.getPostedBy().getId().equals(admin.getId())) {
            notifications.notify(h.getPostedBy(), "❌", "“" + h.getTitle() + "” wasn't approved",
                    h.getRejectionReason() == null ? "An admin reviewed your post." : "Reason: " + h.getRejectionReason(),
                    Link.HACKATHON, h.getId());
        }
        return toDto(me, h);
    }

    @Transactional
    public HackathonDto update(AuthUser me, Long id, HackathonRequest req) {
        requireAdmin(me);
        Hackathon h = find(me, id);
        apply(h, req);
        return toDto(me, h);
    }

    /** Removes the listing. Its chat channel (and message history) is kept. */
    @Transactional
    public void delete(AuthUser me, Long id) {
        requireAdmin(me);
        Hackathon h = find(me, id);
        if (!teamRepo.findByHackathonId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Teams have formed for this hackathon, so it can't be deleted. Edit it instead.");
        }
        interests.deleteByHackathonId(id);
        teamPosts.deleteByHackathonId(id);
        hackathons.delete(h);
    }

    // ---------- internals ----------

    private void openChannel(Hackathon h) {
        if (h.getChannel() != null) return;
        String about = "Chat for " + h.getTitle() + (h.getOrganizer() != null ? " by " + h.getOrganizer() : "") + ".";
        Channel c = chat.createHackathonChannel(h.getPostedBy(), h.getTitle(), about);
        h.setChannel(c);
    }

    private void apply(Hackathon h, HackathonRequest r) {
        if (r == null) throw bad("Missing hackathon details.");
        String title = blankToNull(r.title(), 100);
        if (title == null) throw bad("Give the hackathon a name.");
        if (r.startDate() == null || r.endDate() == null) throw bad("Start and end dates are required.");
        if (r.endDate().isBefore(r.startDate())) throw bad("End date can't be before the start date.");
        if (r.registrationDeadline() != null && r.registrationDeadline().isAfter(r.endDate())) {
            throw bad("Registration deadline can't be after the hackathon ends.");
        }
        String url = blankToNull(r.websiteUrl(), 500);
        if (url != null && !url.matches("(?i)^https?://\\S+$")) {
            throw bad("Website must be a full link starting with http:// or https://");
        }
        h.setTitle(title);
        h.setOrganizer(blankToNull(r.organizer(), 100));
        h.setDescription(blankToNull(r.description(), 2000));
        h.setWebsiteUrl(url);
        h.setMode(r.mode() == null ? Hackathon.Mode.ONLINE : r.mode());
        h.setLocation(blankToNull(r.location(), 120));
        h.setStartDate(r.startDate());
        h.setEndDate(r.endDate());
        h.setRegistrationDeadline(r.registrationDeadline());
        h.setPrizePool(blankToNull(r.prizePool(), 60));
    }

    private List<HackathonDto> toDtos(AuthUser me, List<Hackathon> list) {
        Map<Long, Long> counts = interests.countByWorkspace(me.workspaceId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Set<Long> mine = interests.findHackathonIdsByUserId(me.userId());
        Map<Long, Long> teamCounts = teamPostCounts(me);
        return list.stream().map(h -> toDto(me, h, counts.getOrDefault(h.getId(), 0L), mine.contains(h.getId()),
                teamCounts.getOrDefault(h.getId(), 0L))).toList();
    }

    private HackathonDto toDto(AuthUser me, Hackathon h) {
        boolean interested = interests.findByHackathonIdAndUserId(h.getId(), me.userId()).isPresent();
        return toDto(me, h, interests.countByHackathonId(h.getId()), interested,
                teamPostCounts(me).getOrDefault(h.getId(), 0L));
    }

    private Map<Long, Long> teamPostCounts(AuthUser me) {
        return teamPosts.countOpenByWorkspace(me.workspaceId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private HackathonDto toDto(AuthUser me, Hackathon h, long count, boolean interested, long openTeamPosts) {
        Channel c = h.getChannel();
        User p = h.getPostedBy();
        return new HackathonDto(h.getId(), h.getTitle(), h.getOrganizer(), h.getDescription(), h.getWebsiteUrl(),
                h.getMode().name(), h.getLocation(), h.getStartDate(), h.getEndDate(), h.getRegistrationDeadline(),
                h.getPrizePool(), h.getSource().name(), h.getStatus().name(), h.getRejectionReason(),
                new PersonRef(p.getId(), p.getDisplayName()),
                c == null ? null : new ChannelRef(c.getId(), c.getName(), c.getNexconnId()),
                count, interested, p.getId().equals(me.userId()), h.getCreatedAt(), openTeamPosts);
    }

    private Hackathon find(AuthUser me, Long id) {
        return hackathons.findById(id)
                .filter(h -> h.getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hackathon not found"));
    }

    private User load(AuthUser me) {
        return users.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    /** Role is read from the DB (not the JWT) so a demoted admin loses access immediately. */
    private User requireAdmin(AuthUser me) {
        User u = load(me);
        if (!isAdmin(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only workspace admins can do that.");
        return u;
    }

    private static boolean isAdmin(User u) {
        return u.getRole() == User.Role.ADMIN;
    }

    private static String whenText(Hackathon h) {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM");
        String when = h.getStartDate().equals(h.getEndDate())
                ? h.getStartDate().format(fmt)
                : h.getStartDate().format(fmt) + " – " + h.getEndDate().format(fmt);
        return when + (h.getRegistrationDeadline() != null
                ? " · register by " + h.getRegistrationDeadline().format(fmt) : "");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static String blankToNull(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
