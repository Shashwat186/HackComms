package com.shashwat.hackcomms.notification;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.notification.Notification.Link;
import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * In-app notifications. Other services call the {@code notify*} methods; the UI polls
 * {@code /api/notifications/unread-count} and loads the list when the bell is opened.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public record NotificationDto(Long id, String icon, String title, String body, String link, Long linkId,
                                  boolean read, Instant createdAt) {}

    public record Inbox(long unread, List<NotificationDto> items) {}

    // ---------- sending (joins the caller's transaction) ----------

    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(User to, String icon, String title, String body, Link link, Long linkId) {
        notifyOnce(to, icon, title, body, link, linkId, null);
    }

    /** Like {@link #notify} but skipped if this user already got one with the same key. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyOnce(User to, String icon, String title, String body, Link link, Long linkId, String dedupeKey) {
        if (to == null) return;
        if (dedupeKey != null && notifications.existsByUserIdAndDedupeKey(to.getId(), dedupeKey)) return;
        Notification n = new Notification();
        n.setUser(to);
        n.setIcon(icon);
        n.setTitle(truncate(title, 120));
        n.setBody(body == null ? null : truncate(body, 280));
        n.setLink(link == null ? Link.NONE : link);
        n.setLinkId(linkId);
        n.setDedupeKey(dedupeKey);
        notifications.save(n);
    }

    /** Everyone in the list except {@code skip} (usually whoever caused the event). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAll(Collection<User> to, User skip, String icon, String title, String body, Link link, Long linkId) {
        to.stream()
                .filter(Objects::nonNull)
                .filter(u -> skip == null || !u.getId().equals(skip.getId()))
                .distinct()
                .forEach(u -> notify(u, icon, title, body, link, linkId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyWorkspace(Long workspaceId, User skip, String icon, String title, String body, Link link, Long linkId) {
        notifyAll(users.findByWorkspaceIdOrderByDisplayNameAsc(workspaceId), skip, icon, title, body, link, linkId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyAdmins(Long workspaceId, User skip, String icon, String title, String body, Link link, Long linkId) {
        notifyAll(users.findByWorkspaceIdOrderByDisplayNameAsc(workspaceId).stream()
                .filter(u -> u.getRole() == User.Role.ADMIN).toList(), skip, icon, title, body, link, linkId);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public Inbox inbox(AuthUser me) {
        List<NotificationDto> items = notifications.findByUserIdOrderByCreatedAtDesc(me.userId(), PageRequest.of(0, 50))
                .stream().map(NotificationService::toDto).toList();
        return new Inbox(notifications.countByUserIdAndReadFalse(me.userId()), items);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return notifications.countByUserIdAndReadFalse(me.userId());
    }

    @Transactional
    public void markRead(AuthUser me, Long id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getUser().getId().equals(me.userId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(AuthUser me) {
        notifications.markAllRead(me.userId());
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getIcon(), n.getTitle(), n.getBody(), n.getLink().name(), n.getLinkId(),
                n.isRead(), n.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
