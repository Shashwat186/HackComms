package com.shashwat.hackcomms.chat;

import com.shashwat.hackcomms.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chat;

    public record CreateChannelRequest(String name, String description) {}

    public record IdsRequest(List<String> ids) {}

    /** App Key + this user's Nexconn token. Called once when the workspace page opens. */
    @GetMapping("/chat/session")
    public ChatService.Session session(@AuthenticationPrincipal AuthUser me) {
        return chat.session(me);
    }

    @GetMapping("/channels")
    public List<ChatService.ChannelDto> channels(@AuthenticationPrincipal AuthUser me) {
        return chat.listChannels(me);
    }

    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.ChannelDto create(@AuthenticationPrincipal AuthUser me, @RequestBody CreateChannelRequest body) {
        return chat.createChannel(me, body.name(), body.description());
    }

    @PostMapping("/channels/{id}/join")
    public ChatService.ChannelDto join(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return chat.joinChannel(me, id);
    }

    @GetMapping("/people")
    public List<ChatService.PersonDto> people(@AuthenticationPrincipal AuthUser me) {
        return chat.people(me);
    }

    // Lookups used by the Chat UI's ServiceHooks to show names instead of raw IDs.

    @PostMapping("/chat/profiles/users")
    public List<ChatService.UserProfile> userProfiles(@AuthenticationPrincipal AuthUser me, @RequestBody IdsRequest body) {
        return chat.userProfiles(me, safe(body));
    }

    @PostMapping("/chat/profiles/groups")
    public List<ChatService.GroupProfile> groupProfiles(@AuthenticationPrincipal AuthUser me, @RequestBody IdsRequest body) {
        return chat.groupProfiles(me, safe(body));
    }

    @GetMapping("/chat/groups/{groupId}/members")
    public List<ChatService.GroupMember> groupMembers(@AuthenticationPrincipal AuthUser me, @PathVariable String groupId) {
        return chat.groupMembers(me, groupId);
    }

    private static List<String> safe(IdsRequest body) {
        if (body == null || body.ids() == null) return List.of();
        return body.ids().stream().limit(200).toList();
    }
}
