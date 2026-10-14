package com.ticketrush.identity;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketrush.common.security.Role;

/** Chỉ ADMIN (chặn trong SecurityConfig theo đường dẫn /admin/**). */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final AccountService accounts;

    AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizerResponse createOrganizer(@Valid @RequestBody CreateOrganizerRequest request) {
        return new OrganizerResponse(accounts.createOrganizer(request.name()), request.name().trim());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    AuthController.UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return AuthController.UserResponse.of(accounts.createUser(request.email(), request.password(),
                request.fullName(), request.role(), request.organizerId()));
    }

    record CreateOrganizerRequest(@NotBlank @Size(max = 200) String name) {}

    record OrganizerResponse(UUID id, String name) {}

    record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 200) String fullName,
            @NotNull Role role,
            UUID organizerId) {}
}
