package com.m2m.internal.auth.server;

import io.jsonwebtoken.Jwts;
import lombok.AllArgsConstructor;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * RSA-based implementation using JJWT.
 */
@AllArgsConstructor
public final class RsaInternalTokenIssuer implements InternalTokenIssuer {
    private final KeyPair keyPair;
    private final String issuer;
    private final String internalAudience;
    private final long accessInternalTtlSeconds;

    @Override
    public Issued issueInternalClientJwt(String clientId, List<String> scopes) {
        Instant now = Instant.now();
        Instant exp =  now.plusSeconds(accessInternalTtlSeconds);

        String jwt = Jwts.builder()
            .issuer(issuer)
            .subject(clientId)
            .audience().add(internalAudience).and()
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("scp", scopes)
            .signWith(keyPair.getPrivate())
            .compact();

        return new Issued(jwt, accessInternalTtlSeconds, exp);
    }
}
