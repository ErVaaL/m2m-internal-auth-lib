package com.m2m.internal.auth;

import java.net.http.HttpRequest;

public record InternalAuthHeader(InternalAccessTokenManager tokenManager, String headerName) {

    public InternalAuthHeader(InternalAccessTokenManager tokenManager, String headerName) {
        this.tokenManager = tokenManager;
        this.headerName = (headerName == null || headerName.isBlank())
            ? "Secured-Authorization"
            : headerName;
    }

    public HttpRequest.Builder add(HttpRequest.Builder builder) {
        return builder.header(headerName, "Bearer " + tokenManager.currentToken());
    }

}
