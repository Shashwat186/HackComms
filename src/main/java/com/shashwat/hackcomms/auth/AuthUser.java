package com.shashwat.hackcomms.auth;

/** The authenticated principal, read from the JWT. Use with {@code @AuthenticationPrincipal AuthUser me}. */
public record AuthUser(Long userId, Long workspaceId, String email, String role) {
}
