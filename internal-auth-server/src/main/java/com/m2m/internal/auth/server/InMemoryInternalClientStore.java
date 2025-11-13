package com.m2m.internal.auth.server;

import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
public class InMemoryInternalClientStore implements InternalClientStore{

    public record Client(String id, String secret, Set<String> scopes){}

    private final Map<String, Client> clients;

    @Override
    public boolean authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {return false;}

        Client c = clients.get(clientId.trim());
        if (c == null || c.secret == null) return false;

        String expected = c.secret;

        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = clientSecret.getBytes(StandardCharsets.UTF_8);
        return constantTimeEquals(a,b);
    }

    @Override
    public Set<String> allowedScopes(String clientId) {
        Client c = clients.get(clientId);
        return c == null ? Set.of() : c.scopes;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        int len = Math.max(a.length, b.length);
        int diff = a.length ^ b.length;
        for (int i = 0; i < len; i++) {
            byte ba = i < a.length ? a[i] : 0;
            byte bb = i < b.length ? b[i] : 0;
            diff |= (ba ^ bb);
        }
        return diff == 0;
    }
}
