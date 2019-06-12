package com.github.the20login.test.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

import static org.junit.Assert.fail;

public class ContainerUtils {
    private static final HttpClient HTTP_CLIENT = HttpClientBuilder.create().build();
    private static int CHECK_INTERVAL_MS = 1000;
    private static int TIMEOUT_MS = 10_000;

    public static void awaitStatus(int port, Status expectedStatus) throws InterruptedException {
        Status lastStatus = Status.UNKNOWN;
        for (int i = 0; i < TIMEOUT_MS / CHECK_INTERVAL_MS; i++) {
            lastStatus = getStatus(port);
            if (lastStatus == expectedStatus) {
                return;
            }
            Thread.sleep(CHECK_INTERVAL_MS);
        }
        fail("Service does not become " + expectedStatus + ", last status is " + lastStatus);
    }

    public static Status getStatus(int port) {
        try {
            HttpResponse response = HTTP_CLIENT
                    .execute(new HttpGet("http://127.0.0.1:" + port + "/status"));
            if (response.getStatusLine().getStatusCode() != 200) {
                return Status.UNKNOWN;
            }
            String status = EntityUtils.toString(response.getEntity());
            return Status.valueOf(status);
        } catch (IOException e) {
            return Status.UNKNOWN;
        }
    }

    public enum Status {
        UP,
        DOWN,
        UNKNOWN
    }
}
