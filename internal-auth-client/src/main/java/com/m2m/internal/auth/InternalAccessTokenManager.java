package com.m2m.internal.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalAccessTokenManager {

    private final InternalOAuthClient client;
    private final AtomicReference<InternalOAuthClient.TokenPayload> cache = new AtomicReference<>();
    private final Duration skew;

    public InternalAccessTokenManager(InternalOAuthClient client, Duration skew) {
        this.client = client;
        this.skew = (skew == null) ? Duration.ofSeconds(30) : skew;
    }

    public String currentToken() {
        var current = cache.get();
        if (current == null || isExpiring(current)) {
            synchronized (this) {
                current = cache.get();
                if (current == null || isExpiring(current)) {
                    cache.set(client.fetchToken());
                    current = cache.get();
                }
            }
        }
        return current.accessToken();
    }

    public void refreshIfNeeded() {
        var token = cache.get();
        if (token == null || isExpiring(token)) {
            try {
                cache.set(client.fetchToken());
            } catch (Exception e) {
                log.warn("Failed to refresh internal token", e);
            }
        }
    }

    private boolean isExpiring(InternalOAuthClient.TokenPayload token) {
        return token.expiresAt().minus(skew).isBefore(Instant.now());
    }
}
