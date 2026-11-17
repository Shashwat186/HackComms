package com.shashwat.hackcomms.auth;

import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public record OtpRequest(String email) {}

    public record VerifyRequest(String email, String code) {}

    public record WorkspaceDto(Long id, String name, String slug, String domain) {}

    public record UserDto(Long id, String email, String displayName, String role, WorkspaceDto workspace) {
        static UserDto of(User u) {
            var w = u.getWorkspace();
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(),
                    new WorkspaceDto(w.getId(), w.getName(), w.getSlug(), w.getDomain()));
        }
    }

    public record LoginResponse(String token, UserDto user) {}

    /** Step 1: send a code. Response tells the UI which college workspace the email maps to. */
    @PostMapping("/auth/otp/request")
    public AuthService.OtpRequested requestOtp(@RequestBody OtpRequest body) {
        return auth.requestOtp(body.email());
    }

    /** Step 2: verify the code; creates the user (and workspace) on first login. */
    @PostMapping("/auth/otp/verify")
    public LoginResponse verify(@RequestBody VerifyRequest body) {
        var result = auth.verifyOtp(body.email(), body.code());
        return new LoginResponse(result.token(), UserDto.of(result.user()));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return users.findById(me.userId())
                .map(UserDto::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }
}
