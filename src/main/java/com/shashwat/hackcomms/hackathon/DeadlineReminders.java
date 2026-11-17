package com.shashwat.hackcomms.hackathon;

import com.shashwat.hackcomms.notification.Notification.Link;
import com.shashwat.hackcomms.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Hourly: remind students who starred a hackathon when registration closes today or tomorrow. */
@Component
@RequiredArgsConstructor
public class DeadlineReminders {

    private final HackathonRepository hackathons;
    private final HackathonInterestRepository interests;
    private final NotificationService notifications;

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1H")
    @Transactional
    public void remind() {
        LocalDate today = LocalDate.now();
        for (Hackathon h : hackathons.findByStatusAndRegistrationDeadlineBetween(Hackathon.Status.APPROVED, today, today.plusDays(1))) {
            boolean isToday = h.getRegistrationDeadline().equals(today);
            for (var u : interests.findInterestedUsers(h.getId())) {
                // One reminder per user per hackathon, however many times this runs.
                notifications.notifyOnce(u, "⏰",
                        "Registration for " + h.getTitle() + " closes " + (isToday ? "today" : "tomorrow"),
                        h.getWebsiteUrl() != null ? "Register at " + h.getWebsiteUrl() : "Don't miss it!",
                        Link.HACKATHON, h.getId(), "deadline:h" + h.getId());
            }
        }
    }
}
