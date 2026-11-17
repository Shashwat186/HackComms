package com.shashwat.hackcomms.profile;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.profile.ProfileService.LeetCodeLink;
import com.shashwat.hackcomms.profile.ProfileService.PersonSummary;
import com.shashwat.hackcomms.profile.ProfileService.ProfileDto;
import com.shashwat.hackcomms.profile.ProfileService.ProfileUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    /** Everyone in the workspace, featured coders first. */
    @GetMapping
    public List<PersonSummary> directory(@AuthenticationPrincipal AuthUser me) {
        return service.directory(me);
    }

    @GetMapping("/featured")
    public List<PersonSummary> featured(@AuthenticationPrincipal AuthUser me) {
        return service.featured(me);
    }

    @GetMapping("/{userId}")
    public ProfileDto get(@AuthenticationPrincipal AuthUser me, @PathVariable Long userId) {
        return service.get(me, userId);
    }

    @PutMapping("/me")
    public ProfileDto update(@AuthenticationPrincipal AuthUser me, @RequestBody ProfileUpdate body) {
        return service.update(me, body);
    }

    @PostMapping("/me/leetcode")
    public ProfileDto link(@AuthenticationPrincipal AuthUser me, @RequestBody LeetCodeLink body) {
        return service.linkLeetcode(me, body.username());
    }

    @PostMapping("/me/leetcode/verify")
    public ProfileDto verify(@AuthenticationPrincipal AuthUser me) {
        return service.verifyLeetcode(me);
    }

    @PostMapping("/me/leetcode/refresh")
    public ProfileDto refresh(@AuthenticationPrincipal AuthUser me) {
        return service.refreshLeetcode(me);
    }

    @DeleteMapping("/me/leetcode")
    public ProfileDto unlink(@AuthenticationPrincipal AuthUser me) {
        return service.unlinkLeetcode(me);
    }
}
