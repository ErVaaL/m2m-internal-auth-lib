package com.m2m.internal.auth.server;

import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Core business logic for the client_credentials token endpoint.
 */
@AllArgsConstructor
public class InternalOAuthService {

    public record TokenRequest(
        String authHeader,
        String grantType,
        String scope
    ){}

    public sealed interface TokenResult {
        record Success(
            String accessToken,
            String tokenType,
            long expiresIn,
            String scope,
            Instant exp
        ) implements TokenResult {
        }
        record Error(int httpStatus, String errorCode) implements TokenResult {}
    }

    private final InternalClientStore clientStore;
    private final InternalTokenIssuer tokenIssuer;

    public TokenResult token(TokenRequest req) {
        if (!"client_credentials".equals(req.grantType())) return new TokenResult.Error(400, "unsupported_grant_type");

        String[] creds = parseBasic(req.authHeader());
        if (creds == null) return new TokenResult.Error(401, "invalid_client");

        String clientId = creds[0];
        String clientSecret = creds[1];

        if (!clientStore.authenticate(clientId, clientSecret)) return new TokenResult.Error(401, "invalid_client");

        Set<String> allowed = clientStore.allowedScopes(clientId);
        Set<String> requested = req.scope() == null || req.scope().isBlank()
            ? new HashSet<>(allowed)
            : new HashSet<>(Arrays.stream(req.scope().split("\\s+"))
            .filter(s -> !s.isBlank())
            .toList());

        requested.retainAll(allowed);

        var issued = tokenIssuer.issueInternalClientJwt(clientId, List.copyOf(requested));

        return new TokenResult.Success(
            issued.token(),
            "Bearer",
            issued.expiresInSeconds(),
            String.join(" ", requested),
            issued.expiresAt()
        );
    }

    private static String[] parseBasic(String auth) {
        if (auth == null || !auth.startsWith("Basic ")) return null;
        byte[] raw = Base64.getDecoder().decode(auth.substring(6));
        String s = new String(raw, StandardCharsets.UTF_8);
        int idx = s.indexOf(":");
        if (idx <= 0) return null;
        return new String[]{s.substring(0, idx), s.substring(idx + 1)};
    }
}
