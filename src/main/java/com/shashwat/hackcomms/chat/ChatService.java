package com.shashwat.hackcomms.chat;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.hackathon.Hackathon;
import com.shashwat.hackcomms.hackathon.HackathonRepository;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final String GENERAL = "general";
    private static final Pattern CHANNEL_NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");

    private final NexconnClient nexconn;
    private final ChannelRepository channels;
    private final ChannelMemberRepository members;
    private final UserRepository users;
    private final HackathonRepository hackathons;

    public record Session(String appKey, String areaCode, String userId, String token) {}

    /** hackathonId/hackathonSource are set for hackathon channels so the sidebar can group them. */
    public record ChannelDto(Long id, String name, String description, String kind, String nexconnId,
                             long memberCount, boolean joined, Long hackathonId, String hackathonSource) {}

    public record PersonDto(Long id, String nexconnId, String displayName, String email) {}

    public record UserProfile(String userId, String name) {}

    public record GroupProfile(String groupId, String name, long memberCount) {}

    public record GroupMember(String userId, String nickname) {}

    /**
     * Everything the browser needs to connect: registers the user with Nexconn on first use
     * and makes sure they're in #general.
     */
    @Transactional
    public Session session(AuthUser me) {
        requireConfigured();
        User user = load(me);
        ensureRegistered(user);
        Channel general = ensureGeneral(user);
        join(general, user);
        return new Session(nexconn.appKey(), nexconn.areaCode(), user.nexconnId(), user.getNexconnToken());
    }

    @Transactional(readOnly = true)
    public List<ChannelDto> listChannels(AuthUser me) {
        Set<Long> joined = members.findChannelIdsByUserId(me.userId());
        Map<Long, Long> counts = members.countMembersByWorkspace(me.workspaceId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Hackathon> byChannel = hackathons.findByWorkspaceIdAndChannelIsNotNull(me.workspaceId()).stream()
                .collect(Collectors.toMap(h -> h.getChannel().getId(), h -> h, (a, b) -> a));
        return channels.findByWorkspaceIdOrderByKindAscNameAsc(me.workspaceId()).stream()
                .filter(c -> c.getKind() != Channel.Kind.TEAM) // private; listed via /api/teams for members only
                .map(c -> toDto(c, counts.getOrDefault(c.getId(), 0L), joined.contains(c.getId()), byChannel.get(c.getId())))
                .toList();
    }

    /**
     * Creates the chat channel for a newly approved hackathon. The poster owns it and is its first member.
     * Name is derived from the title ("Smart India Hackathon 2026" -> #smart-india-hackathon-2026).
     */
    @Transactional
    public Channel createHackathonChannel(User owner, String title, String description) {
        requireConfigured();
        ensureRegistered(owner);
        String base = slugify(title);
        String name = base;
        for (int n = 2; channels.existsByWorkspaceIdAndName(owner.getWorkspace().getId(), name); n++) {
            name = base + "-" + n;
        }
        return createGroupChannel(owner, name, description, Channel.Kind.HACKATHON);
    }

    @Transactional
    public ChannelDto createChannel(AuthUser me, String rawName, String description) {
        requireConfigured();
        User user = load(me);
        // Public channels shape the whole workspace, so only admins create them.
        // Students still get private team channels and hackathon channels (via approval).
        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only workspace admins can create public channels. Create a private team channel instead.");
        }
        String name = normaliseName(rawName);
        if (channels.existsByWorkspaceIdAndName(me.workspaceId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "#" + name + " already exists.");
        }
        ensureRegistered(user);
        Channel c = createGroupChannel(user, name, trimToNull(description), Channel.Kind.TOPIC);
        return toDto(c, 1, true, null);
    }

    @Transactional
    public ChannelDto joinChannel(AuthUser me, Long channelId) {
        requireConfigured();
        User user = load(me);
        Channel c = channels.findById(channelId)
                .filter(ch -> ch.getWorkspace().getId().equals(me.workspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        if (c.getKind() == Channel.Kind.TEAM) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team channels are private. Ask the team leader to add you.");
        }
        ensureRegistered(user);
        join(c, user);
        Hackathon h = hackathons.findByWorkspaceIdAndChannelIsNotNull(me.workspaceId()).stream()
                .filter(x -> x.getChannel().getId().equals(c.getId())).findFirst().orElse(null);
        return toDto(c, members.countByChannelId(c.getId()), true, h);
    }

    @Transactional(readOnly = true)
    public List<PersonDto> people(AuthUser me) {
        return users.findByWorkspaceIdOrderByDisplayNameAsc(me.workspaceId()).stream()
                .map(u -> new PersonDto(u.getId(), u.nexconnId(), u.getDisplayName(), u.getEmail()))
                .toList();
    }

    // ---------- Profile lookups for the Nexconn Chat UI hooks (scoped to the caller's workspace) ----------

    @Transactional(readOnly = true)
    public List<UserProfile> userProfiles(AuthUser me, List<String> nexconnIds) {
        Map<Long, String> idToNexconn = new HashMap<>();
        for (String nid : nexconnIds) {
            parseUserId(nid).ifPresent(id -> idToNexconn.put(id, nid));
        }
        Map<String, String> names = users.findByWorkspaceIdAndIdIn(me.workspaceId(), idToNexconn.keySet()).stream()
                .collect(Collectors.toMap(User::nexconnId, User::getDisplayName));
        // Always answer for every requested ID so the UI doesn't keep re-asking.
        return nexconnIds.stream().map(id -> new UserProfile(id, names.getOrDefault(id, "Unknown user"))).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupProfile> groupProfiles(AuthUser me, List<String> groupIds) {
        Map<String, Channel> found = channels.findByNexconnIdIn(groupIds).stream()
                .filter(c -> c.getWorkspace().getId().equals(me.workspaceId()))
                .collect(Collectors.toMap(Channel::getNexconnId, c -> c));
        return groupIds.stream().map(gid -> {
            Channel c = found.get(gid);
            if (c == null) return new GroupProfile(gid, "Unknown channel", 0);
            if (c.getKind() == Channel.Kind.TEAM) {
                return members.existsByChannelIdAndUserId(c.getId(), me.userId())
                        ? new GroupProfile(gid, "🔒 " + c.getName(), members.countByChannelId(c.getId()))
                        : new GroupProfile(gid, "Private channel", 0);
            }
            return new GroupProfile(gid, "#" + c.getName(), members.countByChannelId(c.getId()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMember> groupMembers(AuthUser me, String groupId) {
        return channels.findByNexconnId(groupId)
                .filter(c -> c.getWorkspace().getId().equals(me.workspaceId()))
                .filter(c -> c.getKind() != Channel.Kind.TEAM || members.existsByChannelIdAndUserId(c.getId(), me.userId()))
                .map(c -> members.findByChannelId(c.getId()).stream()
                        .map(m -> new GroupMember(m.getUser().nexconnId(), m.getUser().getDisplayName()))
                        .toList())
                .orElse(List.of());
    }

    // ---------- private team channels (used by TeamService) ----------

    @Transactional
    public Channel createTeamChannel(User owner, String teamName) {
        requireConfigured();
        ensureRegistered(owner);
        String base = "team-" + slugify(teamName);
        String name = base;
        for (int n = 2; channels.existsByWorkspaceIdAndName(owner.getWorkspace().getId(), name); n++) {
            name = base + "-" + n;
        }
        return createGroupChannel(owner, name, "Private team channel", Channel.Kind.TEAM);
    }

    @Transactional
    public void addToChannel(Channel c, User user) {
        requireConfigured();
        ensureRegistered(user);
        join(c, user);
    }

    /** Removes from Nexconn too, so a removed member stops receiving messages. */
    @Transactional
    public void removeFromChannel(Channel c, User user) {
        requireConfigured();
        callNexconn(() -> {
            nexconn.leaveGroup(c.getNexconnId(), List.of(user.nexconnId()));
            return null;
        });
        members.deleteByChannelIdAndUserId(c.getId(), user.getId());
    }

    /** Read-only = Nexconn "freeze": nobody can send, history stays. */
    public void setReadOnly(Channel c, boolean readOnly) {
        requireConfigured();
        callNexconn(() -> {
            if (readOnly) nexconn.freezeGroup(c.getNexconnId());
            else nexconn.unfreezeGroup(c.getNexconnId());
            return null;
        });
    }

    public boolean isMember(Channel c, Long userId) {
        return members.existsByChannelIdAndUserId(c.getId(), userId);
    }

    public List<User> membersOf(Channel c) {
        return members.findByChannelIdOrderByJoinedAtAsc(c.getId()).stream().map(ChannelMember::getUser).toList();
    }

    // ---------- internals ----------

    private void ensureRegistered(User user) {
        if (user.getNexconnToken() == null) {
            user.setNexconnToken(callNexconn(() -> nexconn.issueToken(user.nexconnId(), user.getDisplayName())));
        }
    }

    private Channel ensureGeneral(User user) {
        Long wsId = user.getWorkspace().getId();
        return channels.findByWorkspaceIdAndName(wsId, GENERAL)
                .orElseGet(() -> createGroupChannel(user, GENERAL,
                        "Workspace-wide chat for everyone at " + user.getWorkspace().getName() + ".",
                        Channel.Kind.GENERAL));
    }

    private Channel createGroupChannel(User owner, String name, String description, Channel.Kind kind) {
        Channel c = channels.save(new Channel(owner.getWorkspace(), name, description, kind, owner));
        c.setNexconnId("ws" + owner.getWorkspace().getId() + "c" + c.getId());
        String displayName = owner.getWorkspace().getName() + " #" + name;
        callNexconn(() -> {
            nexconn.createGroup(c.getNexconnId(), displayName, owner.nexconnId(), List.of());
            return null;
        });
        members.save(new ChannelMember(c, owner));
        return c;
    }

    private void join(Channel c, User user) {
        if (members.existsByChannelIdAndUserId(c.getId(), user.getId())) return;
        callNexconn(() -> {
            nexconn.joinGroup(c.getNexconnId(), List.of(user.nexconnId()));
            return null;
        });
        members.save(new ChannelMember(c, user));
    }

    private <T> T callNexconn(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (NexconnException e) {
            log.error("Nexconn call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Chat service error (" + e.getCode() + "). Check the Nexconn App Key/Secret and data center.");
        }
    }

    private void requireConfigured() {
        if (!nexconn.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chat isn't configured yet: add your Nexconn App Key and Secret to secrets.properties.");
        }
    }

    private User load(AuthUser me) {
        return users.findById(me.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private ChannelDto toDto(Channel c, long memberCount, boolean joined, Hackathon h) {
        return new ChannelDto(c.getId(), c.getName(), c.getDescription(), c.getKind().name(), c.getNexconnId(),
                memberCount, joined, h == null ? null : h.getId(), h == null ? null : h.getSource().name());
    }

    static String slugify(String title) {
        String s = title == null ? "" : title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 34) s = s.substring(0, 34).replaceAll("-$", "");
        return s.length() < 2 ? "hackathon" : s;
    }

    static String normaliseName(String raw) {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^#", "")
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
        if (!CHANNEL_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Channel names need 2–40 letters, numbers or dashes.");
        }
        return name;
    }

    private static Optional<Long> parseUserId(String nexconnId) {
        if (nexconnId == null || !nexconnId.matches("u\\d+")) return Optional.empty();
        return Optional.of(Long.parseLong(nexconnId.substring(1)));
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 280 ? t.substring(0, 280) : t;
    }
}
