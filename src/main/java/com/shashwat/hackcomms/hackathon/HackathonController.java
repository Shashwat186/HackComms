package com.shashwat.hackcomms.hackathon;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.hackathon.HackathonService.HackathonDto;
import com.shashwat.hackcomms.hackathon.HackathonService.HackathonRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HackathonController {

    private final HackathonService service;

    public record ApproveRequest(boolean official) {}

    public record RejectRequest(String reason) {}

    /** Approved hackathons + the caller's own pending/rejected posts. */
    @GetMapping("/hackathons")
    public List<HackathonDto> list(@AuthenticationPrincipal AuthUser me) {
        return service.listVisible(me);
    }

    @PostMapping("/hackathons")
    @ResponseStatus(HttpStatus.CREATED)
    public HackathonDto create(@AuthenticationPrincipal AuthUser me, @RequestBody HackathonRequest body) {
        return service.create(me, body);
    }

    @PostMapping("/hackathons/{id}/interest")
    public HackathonDto toggleInterest(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.toggleInterest(me, id);
    }

    // ---------- admin ----------

    @GetMapping("/admin/hackathons")
    public List<HackathonDto> all(@AuthenticationPrincipal AuthUser me) {
        return service.listAll(me);
    }

    @PostMapping("/admin/hackathons/{id}/approve")
    public HackathonDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                @RequestBody(required = false) ApproveRequest body) {
        return service.approve(me, id, body != null && body.official());
    }

    @PostMapping("/admin/hackathons/{id}/reject")
    public HackathonDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @RequestBody(required = false) RejectRequest body) {
        return service.reject(me, id, body == null ? null : body.reason());
    }

    @PutMapping("/admin/hackathons/{id}")
    public HackathonDto update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @RequestBody HackathonRequest body) {
        return service.update(me, id, body);
    }

    @DeleteMapping("/admin/hackathons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }
}
