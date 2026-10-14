package com.ticketrush.common.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ticketrush.common.error.ErrorCode;
import com.ticketrush.common.error.ProblemDetails;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /** Đường dẫn không cần JWT. Webhook được bảo vệ bằng chữ ký, không phải JWT. */
    private static final String[] PUBLIC_POST = {"/auth/register", "/auth/login", "/webhooks/payments/**"};
    private static final String[] PUBLIC_GET = {
            "/events", "/events/*", "/events/*/seats", "/payments/return", "/ticket-keys", "/actuator/health"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                 // API stateless, không dùng cookie
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/organizer/**").hasRole(Role.ORGANIZER.name())
                        .requestMatchers(HttpMethod.POST, "/orders").hasRole(Role.CUSTOMER.name())
                        .requestMatchers("/checkin").hasRole(Role.STAFF.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthenticated(json)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthenticated(json))
                        .accessDeniedHandler(forbidden(json)));
        return http.build();
    }

    /**
     * Không đọc header Authorization ở các đường dẫn công khai: token hết hạn gửi kèm theo thói quen
     * không làm hỏng request (401 trên một trang ai cũng xem được), và webhook không bao giờ dựa vào JWT.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        PathPatternParser parser = PathPatternParser.defaultInstance;
        List<PathPattern> publicGet = Arrays.stream(PUBLIC_GET).map(parser::parse).toList();
        List<PathPattern> publicPost = Arrays.stream(PUBLIC_POST).map(parser::parse).toList();
        return request -> {
            PathContainer path = PathContainer.parsePath(
                    request.getRequestURI().substring(request.getContextPath().length()));
            List<PathPattern> open = switch (request.getMethod()) {
                case "GET" -> publicGet;
                case "POST" -> publicPost;
                default -> List.of();
            };
            if (open.stream().anyMatch(pattern -> pattern.matches(path))) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(JwtClaims.ROLE);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority(Role.valueOf(role).authority()));
        });
        return converter;
    }

    private static AuthenticationEntryPoint unauthenticated(JsonMapper json) {
        return (request, response, e) -> write(json, response,
                ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "Thiếu hoặc sai access token"));
    }

    private static AccessDeniedHandler forbidden(JsonMapper json) {
        return (request, response, e) -> write(json, response,
                ProblemDetails.of(ErrorCode.FORBIDDEN, "Vai trò của bạn không được phép thực hiện thao tác này"));
    }

    private static void write(JsonMapper json, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ProblemDetails.toMap(problem));
    }

    @Bean
    WebMvcConfigurer authUserArgumentResolver() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new AuthUserArgumentResolver());
            }
        };
    }
}
