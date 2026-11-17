package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.chat.Channel;
import com.shashwat.hackcomms.chat.ChatService;
import com.shashwat.hackcomms.hackathon.Hackathon;
import com.shashwat.hackcomms.hackathon.HackathonRepository;
import com.shashwat.hackcomms.notification.Notification.Link;
import com.shashwat.hackcomms.notification.NotificationService;
import com.shashwat.hackcomms.profile.ProfileService;
import com.shashwat.hackcomms.profile.ProfileService.PersonSummary;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teams;
    private final UnarchiveRequestRepository requests;
    private final HackathonRepository hackathons;
    private final UserRepository users;
    private final TeamPostRepository teamPosts;
    private final ChatService chat;
    private final ProfileService profiles;
    private final NotificationService notifications;

    /** Days after the hackathon ends before the team channel becomes a read-only archive. */
    @Value("${hackcomms.teams.archive-after-days:7}")
    private int archiveAfterDays;

    /** How long an admin-approved unarchive keeps the channel open. */
    @Value("${hackcomms.teams.unarchive-days:14}")
    private int unarchiveDays;

    public record CreateTeamRequest(Long hackathonId, String name, Integer maxSize) {}

    public record AddMemberRequest(Long userId) {}

    public record UnarchiveBody(String reason) {}

    public record ReviewBody(String note) {}

    public record HackathonRef(Long id, String title, LocalDate endDate) {}

    public record ChannelRef(Long id, String name, String nexconnId) {}

    public record RequestRef(Long id, String requestedBy, String reason, Instant createdAt) {}

    public record TeamDto(Long id, String name, HackathonRef hackathon, ChannelRef channel, Long leaderId,
                          List<PersonSummary> members, int maxSize, boolean archived, Instant archivedAt,
                          LocalDate archivesOn, RequestRef pendingRequest, boolean amLeader) {}

    public record UnarchiveRequestDto(Long id, Long teamId, String teamName, String hackathonTitle,
                                      List<String> memberNames, String requestedBy, String reason, String status,
                                      String reviewedBy, String reviewNote, Instant createdAt, Instant archivedAt) {}

    // ---------- member-facing ----------

    @Transactional
    public List<TeamDto> myTeams(AuthUser me) {
        List<Team> mine = teams.findForMember(me.userId());
        mine.forEach(this::archiveIfDue); // so the demo doesn't depend on the hourly job
        return mine.stream().map(t -> toDto(me, t)).toList();
    }

    @Transactional
    public TeamDto create(AuthUser me, CreateTeamRequest req) {
        if (req == null || req.hackathonId() == null) throw bad("Pick the hackathon this team is for.");
        User leader = load(me);
        Hackathon h = hackathons.findById(req.hackathonId())
                .filter(x -> x.getWorkspace().getId().equals(me.workspaceId()))
                .filter(x -> x.getStatus() == Hackathon.Status.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hackathon not found"));
        if (h.getEndDate().isBefore(LocalDate.now())) throw bad("This hackathon has already ended.");
        if (teams.userHasTeamFor(h.getId(), leader.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You're already in a team for " + h.getTitle() + ".");
        }
        String name = req.name() == null ? "" : req.name().trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 40) throw bad("Team names need 2–40 characters.");
        int max = req.maxSize() == null ? 4 : req.maxSize();
        if (max < 2 || max > 8) throw bad("Team size should be between 2 and 8.");

        Team t = new Team();
        t.setWorkspace(leader.getWorkspace());
        t.setHackathon(h);
        t.setName(name);
        t.setLeader(leader);
        t.setMaxSize(max);
        t.setChannel(chat.createTeamChannel(leader, name));
        return toDto(me, teams.save(t));
    }

    @Transactional
    public TeamDto addMember(AuthUser me, Long teamId, Long userId) {
        Team t = find(me, teamId);
        requireLeader(me, t);
        if (t.isArchived()) throw bad("This team is archived. Request an unarchive first.");
        User u = users.findById(userId)
                .filter(x -> x.getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (chat.isMember(t.getChannel(), u.getId())) return toDto(me, t);
        int size = chat.membersOf(t.getChannel()).size();
        if (size >= t.getMaxSize()) throw new ResponseStatusException(HttpStatus.CONFLICT, "The team is full (" + t.getMaxSize() + ").");
        if (teams.userHasTeamFor(t.getHackathon().getId(), u.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    u.getDisplayName() + " is already in another team for this hackathon.");
        }
        chat.addToChannel(t.getChannel(), u);
        if (size + 1 >= t.getMaxSize()) closeHiringPosts(t);
        notifications.notify(u, "🔒", "You joined team " + t.getName(),
                t.getLeader().getDisplayName() + " added you for " + t.getHackathon().getTitle() + ". Say hi in the team chat!",
                Link.TEAM, t.getId());
        return toDto(me, t);
    }

    /** Leader removes someone, or anyone removes themselves (leave). */
    @Transactional
    public TeamDto removeMember(AuthUser me, Long teamId, Long userId) {
        Team t = find(me, teamId);
        boolean self = userId.equals(me.userId());
        if (!self) requireLeader(me, t);
        User u = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (!chat.isMember(t.getChannel(), u.getId())) return toDto(me, t);
        // The leader created the Nexconn group and owns it; Nexconn doesn't let an owner leave (25417).
        if (t.getLeader().getId().equals(u.getId())) {
            throw bad("As the team leader you can't leave. Remove members, or archive the team once you're done.");
        }
        chat.removeFromChannel(t.getChannel(), u);
        if (!self) {
            notifications.notify(u, "👋", "You were removed from team " + t.getName(),
                    "You no longer have access to its private chat.", Link.NONE, null);
        }
        return self ? null : toDto(me, t);
    }

    /** Leader (or admin) can archive early, e.g. once results are out. */
    @Transactional
    public TeamDto archiveNow(AuthUser me, Long teamId) {
        Team t = find(me, teamId);
        if (!isLeader(me, t) && !isAdmin(me)) throw forbidden("Only the team leader or an admin can archive.");
        if (!t.isArchived()) archive(t);
        return toDto(me, t);
    }

    @Transactional
    public TeamDto requestUnarchive(AuthUser me, Long teamId, String reason) {
        Team t = find(me, teamId);
        if (!chat.isMember(t.getChannel(), me.userId())) throw forbidden("Only team members can ask for this.");
        if (!t.isArchived()) throw bad("This team isn't archived.");
        if (requests.findFirstByTeamIdAndStatus(t.getId(), UnarchiveRequest.Status.PENDING).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A request is already waiting for the admin.");
        }
        UnarchiveRequest r = new UnarchiveRequest();
        r.setTeam(t);
        r.setRequestedBy(load(me));
        r.setReason(clean(reason));
        requests.save(r);
        notifications.notifyAdmins(me.workspaceId(), r.getRequestedBy(), "📦", "Unarchive request: " + t.getName(),
                r.getRequestedBy().getDisplayName() + " asked to reopen the team chat"
                        + (r.getReason() == null ? "." : ": “" + r.getReason() + "”"), Link.ADMIN, null);
        return toDto(me, t);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public List<UnarchiveRequestDto> listRequests(AuthUser me) {
        requireAdmin(me);
        return requests.findForWorkspace(me.workspaceId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public UnarchiveRequestDto review(AuthUser me, Long requestId, boolean approve, String note) {
        User admin = requireAdmin(me);
        UnarchiveRequest r = requests.findById(requestId)
                .filter(x -> x.getTeam().getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (r.getStatus() != UnarchiveRequest.Status.PENDING) throw bad("This request was already handled.");
        r.setStatus(approve ? UnarchiveRequest.Status.APPROVED : UnarchiveRequest.Status.DENIED);
        r.setReviewedBy(admin);
        r.setReviewNote(clean(note));
        r.setReviewedAt(Instant.now());
        Team t = r.getTeam();
        String noteText = r.getReviewNote() == null ? "" : " Admin: “" + r.getReviewNote() + "”";
        if (approve) {
            chat.setReadOnly(t.getChannel(), false);
            t.setArchived(false);
            t.setArchivedAt(null);
            t.setKeepActiveUntil(LocalDate.now().plusDays(unarchiveDays));
            notifications.notifyAll(chat.membersOf(t.getChannel()), admin, "🔓", "Team " + t.getName() + " is reopened",
                    "You can chat again until " + t.getKeepActiveUntil() + "." + noteText, Link.TEAM, t.getId());
        } else {
            notifications.notify(r.getRequestedBy(), "🚫", "Request to reopen " + t.getName() + " was denied",
                    noteText.isEmpty() ? "The team chat stays archived." : noteText.trim(), Link.TEAM, t.getId());
        }
        return toDto(r);
    }

    // ---------- archiving ----------

    /** Hourly sweep: archive every team whose hackathon ended more than N days ago. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    @Transactional
    public void archiveDueTeams() {
        for (Team t : teams.findByArchivedFalse()) {
            try {
                archiveIfDue(t);
            } catch (RuntimeException e) {
                log.warn("Couldn't auto-archive team {}: {}", t.getId(), e.getMessage());
            }
        }
    }

    private void archiveIfDue(Team t) {
        if (!t.isArchived() && !LocalDate.now().isBefore(archivesOn(t))) archive(t);
    }

    private LocalDate archivesOn(Team t) {
        LocalDate byHackathon = t.getHackathon().getEndDate().plusDays(archiveAfterDays);
        LocalDate keep = t.getKeepActiveUntil();
        return keep != null && keep.isAfter(byHackathon) ? keep : byHackathon;
    }

    private void archive(Team t) {
        chat.setReadOnly(t.getChannel(), true);
        t.setArchived(true);
        t.setArchivedAt(Instant.now());
        notifications.notifyAll(chat.membersOf(t.getChannel()), null, "📦", "Team " + t.getName() + " is now archived",
                "The chat is read-only. Need it again? Ask the admin to reopen it.", Link.TEAM, t.getId());
    }

    private void closeHiringPosts(Team t) {
        teamPosts.findVisible(t.getWorkspace().getId(), t.getLeader().getId()).stream()
                .filter(p -> p.isOpen() && p.getType() == TeamPost.Type.LOOKING_FOR_MEMBERS)
                .filter(p -> p.getAuthor().getId().equals(t.getLeader().getId()))
                .filter(p -> p.getHackathon().getId().equals(t.getHackathon().getId()))
                .forEach(p -> p.setOpen(false));
    }

    // ---------- mapping & guards ----------

    private TeamDto toDto(AuthUser me, Team t) {
        List<User> memberUsers = chat.membersOf(t.getChannel());
        Map<Long, PersonSummary> summaries = profiles.summaries(memberUsers);
        Channel c = t.getChannel();
        RequestRef pending = requests.findFirstByTeamIdAndStatus(t.getId(), UnarchiveRequest.Status.PENDING)
                .map(r -> new RequestRef(r.getId(), r.getRequestedBy().getDisplayName(), r.getReason(), r.getCreatedAt()))
                .orElse(null);
        return new TeamDto(t.getId(), t.getName(),
                new HackathonRef(t.getHackathon().getId(), t.getHackathon().getTitle(), t.getHackathon().getEndDate()),
                new ChannelRef(c.getId(), c.getName(), c.getNexconnId()), t.getLeader().getId(),
                memberUsers.stream().map(u -> summaries.get(u.getId())).toList(), t.getMaxSize(), t.isArchived(),
                t.getArchivedAt(), archivesOn(t), pending, isLeader(me, t));
    }

    private UnarchiveRequestDto toDto(UnarchiveRequest r) {
        Team t = r.getTeam();
        return new UnarchiveRequestDto(r.getId(), t.getId(), t.getName(), t.getHackathon().getTitle(),
                chat.membersOf(t.getChannel()).stream().map(User::getDisplayName).toList(),
                r.getRequestedBy().getDisplayName(), r.getReason(), r.getStatus().name(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getDisplayName(), r.getReviewNote(),
                r.getCreatedAt(), t.getArchivedAt());
    }

    private Team find(AuthUser me, Long id) {
        Team t = teams.findById(id)
                .filter(x -> x.getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        // Private: non-members (other than admins) can't even see it exists.
        if (!chat.isMember(t.getChannel(), me.userId()) && !isAdmin(me)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        return t;
    }

    private boolean isLeader(AuthUser me, Team t) {
        return t.getLeader().getId().equals(me.userId());
    }

    private void requireLeader(AuthUser me, Team t) {
        if (!isLeader(me, t)) throw forbidden("Only the team leader can do that.");
    }

    private boolean isAdmin(AuthUser me) {
        return users.findById(me.userId()).map(u -> u.getRole() == User.Role.ADMIN).orElse(false);
    }

    private User requireAdmin(AuthUser me) {
        User u = load(me);
        if (u.getRole() != User.Role.ADMIN) throw forbidden("Only workspace admins can do that.");
        return u;
    }

    private User load(AuthUser me) {
        return users.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 280 ? t.substring(0, 280) : t;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException forbidden(String msg) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
    }
}
