package com.shashwat.hackcomms.team;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.team.TeamPostService.TeamPostDto;
import com.shashwat.hackcomms.team.TeamPostService.TeamPostRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team-posts")
@RequiredArgsConstructor
public class TeamPostController {

    private final TeamPostService service;

    public record OpenRequest(boolean open) {}

    /** All open teammate posts in the workspace (filtering happens client-side; the list is small). */
    @GetMapping
    public List<TeamPostDto> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamPostDto create(@AuthenticationPrincipal AuthUser me, @RequestBody TeamPostRequest body) {
        return service.create(me, body);
    }

    @PostMapping("/{id}/open")
    public TeamPostDto setOpen(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody OpenRequest body) {
        return service.setOpen(me, id, body.open());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }
}
