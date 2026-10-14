package com.ticketrush.identity;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketrush.common.security.AuthUser;
import com.ticketrush.common.security.Role;

@RestController
class AuthController {

    private final AccountService accounts;

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(accounts.register(request.email(), request.password(), request.fullName()));
    }

    @PostMapping("/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AccountService.LoginResult result = accounts.login(request.email(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(),
                UserResponse.of(result.user()));
    }

    @GetMapping("/me")
    UserResponse me(AuthUser caller) {
        return UserResponse.of(accounts.get(caller.id()));
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 200) String fullName) {}

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}

    record UserResponse(UUID id, String email, String fullName, Role role, UUID organizerId) {
        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                    user.getOrganizerId());
        }
    }
}
