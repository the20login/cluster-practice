package com.github.the20login.test.utils;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

public class ContainerUtils {
    private static final Logger log = LoggerFactory.getLogger(ContainerUtils.class);
    private static int CHECK_INTERVAL_MS = 1000;
    private static int TIMEOUT_MS = 10_000;

    private static RestAssuredConfig CONFIG = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.connection.timeout", 1000)
                    .setParam("http.socket.timeout", 1000));

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
            ExtractableResponse<Response> response = given()
                    .config(CONFIG)
                    .header("Accept", "text/plain")
                    .when().get("http://127.0.0.1:" + port + "/status")
                    .then().extract();
            if (response.statusCode() != 200) {
                return Status.UNKNOWN;
            }

            return Status.valueOf(response.body().asString());
        } catch (Exception e) {
            log.debug("Unable to obtain status", e);
            return Status.UNKNOWN;
        }
    }

    public enum Status {
        UP,
        DOWN,
        UNKNOWN
    }
}
