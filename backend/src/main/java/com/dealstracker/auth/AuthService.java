package com.dealstracker.auth;

import com.dealstracker.domain.User;
import com.dealstracker.domain.UserSession;
import com.dealstracker.repository.UserRepository;
import com.dealstracker.repository.UserSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Email + password accounts with opaque bearer tokens. The frontend keeps the token in an
 * httpOnly cookie and forwards it as {@code Authorization: Bearer <token>}.
 */
@Service
public class AuthService {

    public static final Duration SESSION_TTL = Duration.ofDays(30);

    private static final String BEARER = "Bearer ";

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, UserSessionRepository sessions, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** A new session: the raw token goes to the client, only its hash is stored. */
    public record LoginResult(User user, String token, OffsetDateTime expiresAt) {
    }

    @Transactional
    public LoginResult register(String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(normalized, encoder.encode(password)));
        return startSession(user);
    }

    @Transactional
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(normalize(email))
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        sessions.deleteExpired(OffsetDateTime.now(clock));
        return startSession(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        token(authorizationHeader).map(AuthService::hash).ifPresent(sessions::deleteById);
    }

    /** The logged-in user, or empty for anonymous requests and unknown or expired tokens. */
    @Transactional(readOnly = true)
    public Optional<User> currentUser(String authorizationHeader) {
        return token(authorizationHeader)
                .flatMap(t -> sessions.findByTokenHashAndExpiresAtAfter(hash(t), OffsetDateTime.now(clock)))
                .map(UserSession::getUser);
    }

    public User requireUser(String authorizationHeader) {
        return currentUser(authorizationHeader)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required"));
    }

    private LoginResult startSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(SESSION_TTL);
        sessions.save(new UserSession(hash(token), user, expiresAt));
        return new LoginResult(user, token, expiresAt);
    }

    private static Optional<String> token(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
