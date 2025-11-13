package com.m2m.internal.auth.server;

import java.util.Set;

/**
 * Source of internal clients and their secrets/scopes.
 * Implement this from env, DB, config, etc.
 */
public interface InternalClientStore {

    boolean authenticate(String clientId, String clientSecret);

    Set<String> allowedScopes(String clientId);
}
