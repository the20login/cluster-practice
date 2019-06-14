package com.github.the20login.test.utils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.fail;

public class FutureUtils {
    public static Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public static <T> T assertFuture(CompletableFuture<T> future) {
        return assertFuture(future, DEFAULT_TIMEOUT);
    }

    public static <T> T assertFuture(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted");
        } catch (ExecutionException e) {
            fail("Future completed with exception: " + e);
        } catch (TimeoutException e) {
            fail("Future does not completed under " + timeout + " timeout");
        }
        return null;
    }
}
