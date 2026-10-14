package com.ticketrush.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.ticketrush.common.persistence.AssignedIdEntity;
import com.ticketrush.common.security.Role;

@Entity
@Table(name = "users")
public class User extends AssignedIdEntity {

    private String email;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private UUID organizerId;

    @Column(updatable = false)
    private Instant createdAt;

    protected User() {}

    User(UUID id, String email, String passwordHash, String fullName, Role role, UUID organizerId, Instant now) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.organizerId = organizerId;
        this.createdAt = now;
    }

    public String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
