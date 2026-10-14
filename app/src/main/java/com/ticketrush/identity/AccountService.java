package com.ticketrush.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;
import com.ticketrush.common.error.ValidationException;
import com.ticketrush.common.security.Role;

@Service
public class AccountService {

    private final UserRepository users;
    private final OrganizerAccounts organizers;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final Clock clock;

    /** So khớp với hash giả khi email không tồn tại, để thời gian trả lời không lộ email nào đã đăng ký. */
    private final String dummyHash;

    AccountService(UserRepository users, OrganizerAccounts organizers, PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer, Clock clock) {
        this.users = users;
        this.organizers = organizers;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Khách tự đăng ký: luôn là CUSTOMER. */
    @Transactional
    public User register(String email, String password, String fullName) {
        return createUser(email, password, fullName, Role.CUSTOMER, null);
    }

    /** Admin tạo tài khoản với vai trò bất kỳ. ORGANIZER phải gắn với một ban tổ chức. */
    @Transactional
    public User createUser(String email, String password, String fullName, Role role, UUID organizerId) {
        if (role == Role.ORGANIZER && organizerId == null) {
            throw new ValidationException("Tài khoản ORGANIZER cần organizerId");
        }
        if (role != Role.ORGANIZER && organizerId != null) {
            throw new ValidationException("Chỉ tài khoản ORGANIZER mới gắn với ban tổ chức");
        }
        if (organizerId != null && !organizers.exists(organizerId)) {
            throw new ValidationException("Ban tổ chức không tồn tại");
        }
        String normalized = Emails.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        User user = new User(UUID.randomUUID(), normalized, passwordEncoder.encode(password), fullName.trim(),
                role, organizerId, clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email chạy song song: ràng buộc uq_users_email chặn request thứ hai
            throw emailTaken();
        }
    }

    @Transactional
    public UUID createOrganizer(String name) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        organizers.insert(id, name.trim(), now);
        return id;
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(Emails.normalize(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        JwtIssuer.IssuedToken token = jwtIssuer.issue(user);
        return new LoginResult(token.value(), token.expiresInSeconds(), user);
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(
                ErrorCode.UNAUTHENTICATED, "Tài khoản không còn tồn tại"));
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai email hoặc mật khẩu");
    }

    private static BusinessException emailTaken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email này đã được đăng ký");
    }

    public record LoginResult(String accessToken, long expiresInSeconds, User user) {}
}
