package com.m2m.internal.auth;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TokenRefresher implements AutoCloseable {

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

    public TokenRefresher(InternalAccessTokenManager tm) {
        ses.scheduleWithFixedDelay(tm::refreshIfNeeded, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        ses.shutdownNow();
    }

}
