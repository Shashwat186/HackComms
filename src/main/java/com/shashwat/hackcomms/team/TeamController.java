package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.team.TeamService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService service;

    /** Teams I'm in (active first, then archived). */
    @GetMapping("/teams")
    public List<TeamDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.myTeams(me);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDto create(@AuthenticationPrincipal AuthUser me, @RequestBody CreateTeamRequest body) {
        return service.create(me, body);
    }

    @PostMapping("/teams/{id}/members")
    public TeamDto add(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody AddMemberRequest body) {
        return service.addMember(me, id, body.userId());
    }

    /** Remove a member (leader), or leave (yourself). Leaving returns 204. */
    @DeleteMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<TeamDto> remove(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long userId) {
        TeamDto t = service.removeMember(me, id, userId);
        return t == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(t);
    }

    @PostMapping("/teams/{id}/archive")
    public TeamDto archive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.archiveNow(me, id);
    }

    @PostMapping("/teams/{id}/unarchive-request")
    public TeamDto requestUnarchive(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                    @RequestBody(required = false) UnarchiveBody body) {
        return service.requestUnarchive(me, id, body == null ? null : body.reason());
    }

    // ---------- admin ----------

    @GetMapping("/admin/unarchive-requests")
    public List<UnarchiveRequestDto> requests(@AuthenticationPrincipal AuthUser me) {
        return service.listRequests(me);
    }

    @PostMapping("/admin/unarchive-requests/{id}/approve")
    public UnarchiveRequestDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @RequestBody(required = false) ReviewBody body) {
        return service.review(me, id, true, body == null ? null : body.note());
    }

    @PostMapping("/admin/unarchive-requests/{id}/deny")
    public UnarchiveRequestDto deny(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                    @RequestBody(required = false) ReviewBody body) {
        return service.review(me, id, false, body == null ? null : body.note());
    }
}
