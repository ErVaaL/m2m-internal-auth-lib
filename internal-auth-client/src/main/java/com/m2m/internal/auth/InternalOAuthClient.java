package com.m2m.internal.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public record InternalOAuthClient(HttpClient http, URI tokenUrl, String clientId, String clientSecret, String scope,
                                  ObjectMapper mapper) {

    public InternalOAuthClient(HttpClient http,
                               URI tokenUrl,
                               String clientId,
                               String clientSecret,
                               String scope) {
        this(http, tokenUrl, clientId, clientSecret, scope,
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    public InternalOAuthClient(HttpClient http,
                               URI tokenUrl,
                               String clientId,
                               String clientSecret,
                               String scope,
                               ObjectMapper mapper) {
        this.http = Objects.requireNonNull(http, "http");
        this.tokenUrl = Objects.requireNonNull(tokenUrl, "tokenUrl");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
        this.scope = (scope == null || scope.isBlank()) ? null : scope;
        this.mapper = Objects.requireNonNull(mapper, "mapper");

    }

    public record TokenPayload(String accessToken, long expiresIn, Instant obtainedAt) {
        public Instant expiresAt() {
            return obtainedAt.plusSeconds(expiresIn);
        }
    }

    static final class TokenResponse {
        @JsonProperty("access_token")
        String accessToken;

        @JsonProperty("expires_in")
        long expiresIn;

        @JsonProperty("token_type")
        String tokenType;

        @JsonProperty("scope")
        String scope;
    }

    public TokenPayload fetchToken() {
        String basic = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        StringBuilder form = new StringBuilder("grant_type=client_credentials");
        if (scope != null) {
            form.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder(tokenUrl)
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
            .build();

        int attempts = 0;
        long backoffMs = 500;
        while (true) {
            attempts++;
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int sc = resp.statusCode();
                if (sc >= 200 && sc < 300) {
                    TokenResponse tr = mapper.readValue(resp.body(), TokenResponse.class);
                    if (tr.accessToken == null || tr.expiresIn <= 0) {
                        throw new IllegalStateException("Invalid token response: missing access_token or expires_in");
                    }
                    return new TokenPayload(tr.accessToken, tr.expiresIn, Instant.now());
                } else if (sc >= 500 && attempts < 5) {
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(8000, backoffMs * 2);
                } else {
                    throw new IllegalStateException("Failed to fetch token: HTTP " + sc + " - " + resp.body());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching token", ie);
            } catch (Exception e) {
                if (attempts < 5) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while fetching token", ie);
                    }
                    backoffMs = Math.min(8000, backoffMs * 2);
                } else {
                    throw new RuntimeException("Failed to fetch token", e);
                }
            }
        }
    }
}
