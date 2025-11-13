package com.m2m.internal.auth;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternalOAuthClientConfig {

    private URI tokenUrl;
    private String clientId;
    private String clientSecret;
    private Set<String> scopes = Collections.emptySet();
    private String headerName = "Secured-Authorization";
    private long skewSeconds = 30;

    public String joinedScope() {
        return (scopes == null || scopes.isEmpty()) ? null : String.join(" ", scopes);
    }

}
