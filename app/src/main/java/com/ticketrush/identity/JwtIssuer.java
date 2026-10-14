package com.ticketrush.identity;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.common.security.JwtClaims;

@Component
class JwtIssuer {

    private final JwtEncoder encoder;
    private final TicketRushProperties properties;
    private final Clock clock;

    JwtIssuer(JwtEncoder encoder, TicketRushProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.ROLE, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.jwt().ttl().toSeconds());
    }

    record IssuedToken(String value, long expiresInSeconds) {}
}
