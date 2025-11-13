package com.m2m.internal.auth.server;

import java.time.Instant;
import java.util.List;

/**
 * Issues internal client JWTs for backend-to-backend auth.
 */
public interface InternalTokenIssuer {
    record Issued(String token, long expiresInSeconds, Instant expiresAt){}

    Issued issueInternalClientJwt(String clientId, List<String> scopes);
}
