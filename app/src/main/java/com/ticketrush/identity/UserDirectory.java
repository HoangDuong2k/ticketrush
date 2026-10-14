package com.ticketrush.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketrush.common.security.Role;

/** API công khai của module identity cho các module khác. */
@Service
@Transactional(readOnly = true)
public class UserDirectory {

    private final UserRepository users;

    UserDirectory(UserRepository users) {
        this.users = users;
    }

    public Optional<UserContact> findContact(UUID userId) {
        return users.findById(userId).map(UserDirectory::contact);
    }

    public Optional<UserContact> findByEmail(String email) {
        return users.findByEmail(Emails.normalize(email)).map(UserDirectory::contact);
    }

    /** Ban tổ chức mà tài khoản ORGANIZER thuộc về. */
    public Optional<UUID> organizerIdOf(UUID userId) {
        return users.findById(userId).map(User::getOrganizerId);
    }

    private static UserContact contact(User user) {
        return new UserContact(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    public record UserContact(UUID id, String email, String fullName, Role role) {}
}
