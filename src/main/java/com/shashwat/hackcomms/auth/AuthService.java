package com.shashwat.hackcomms.auth;

import com.shashwat.hackcomms.user.User;
import com.shashwat.hackcomms.user.UserRepository;
import com.shashwat.hackcomms.workspace.CollegeDomain;
import com.shashwat.hackcomms.workspace.Workspace;
import com.shashwat.hackcomms.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otps;
    private final UserRepository users;
    private final WorkspaceService workspaces;
    private final OtpMailer mailer;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Value("${hackcomms.otp.ttl-minutes}")
    private long ttlMinutes;
    @Value("${hackcomms.otp.max-attempts}")
    private int maxAttempts;
    @Value("${hackcomms.otp.resend-cooldown-seconds}")
    private long cooldownSeconds;

    public record OtpRequested(String email, String workspaceName, String workspaceDomain) {}

    public record LoginResult(String token, User user) {}

    @Transactional
    public OtpRequested requestOtp(String rawEmail) {
        CollegeDomain college = parse(rawEmail);
        String email = CollegeDomain.normaliseEmail(rawEmail);

        otps.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.getCreatedAt().plusSeconds(cooldownSeconds).isAfter(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Wait a few seconds before requesting another code.");
            }
        });

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        otps.invalidateAll(email);
        otps.save(new OtpCode(email, encoder.encode(code), Instant.now().plus(Duration.ofMinutes(ttlMinutes))));
        mailer.send(email, code, college.displayName(), ttlMinutes);

        // Workspace isn't created until the code is verified, so typo'd domains don't leave junk behind.
        return new OtpRequested(email, college.displayName(), college.domain());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult verifyOtp(String rawEmail, String code) {
        CollegeDomain college = parse(rawEmail);
        String email = CollegeDomain.normaliseEmail(rawEmail);

        OtpCode otp = otps.findFirstByEmailOrderByCreatedAtDesc(email)
                .filter(o -> !o.isConsumed() && o.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> unauthorized("Code expired or not found. Request a new one."));

        if (otp.getAttempts() >= maxAttempts) {
            otp.setConsumed(true);
            throw unauthorized("Too many wrong attempts. Request a new code.");
        }
        if (code == null || !encoder.matches(code.trim(), otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1); // persisted thanks to noRollbackFor
            throw unauthorized("Incorrect code.");
        }
        otp.setConsumed(true);

        User user = users.findByEmail(email).orElseGet(() -> {
            Workspace ws = workspaces.findOrCreate(college);
            // First student from a college becomes its admin (can create hackathons, run elections).
            User.Role role = users.existsByWorkspaceId(ws.getId()) ? User.Role.MEMBER : User.Role.ADMIN;
            return users.save(new User(email, ws, nameFromEmail(email), role));
        });
        return new LoginResult(jwt.issue(user), user);
    }

    private static CollegeDomain parse(String rawEmail) {
        try {
            return CollegeDomain.fromEmail(rawEmail);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResponseStatusException unauthorized(String msg) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg);
    }

    /** "shashwatsingh.chauhan@jiit.com" -> "Shashwatsingh Chauhan". Users can edit it later. */
    static String nameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return Arrays.stream(local.split("[._\\-+]+"))
                .filter(p -> !p.isBlank())
                .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
