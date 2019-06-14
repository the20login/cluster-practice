package com.github.the20login.integration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.the20login.test.utils.ContainerUtils;
import com.github.the20login.test.utils.GitUtils;
import io.restassured.internal.mapping.Jackson2Mapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static com.github.the20login.test.utils.ContainerUtils.awaitStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

public class ApiTest {
    private static final String REVISION = GitUtils.getRevision();
    private static final String NGINX_CONTAINER_TAG = Optional.ofNullable(System.getProperty("nginxTag")).orElse(REVISION);
    private static final String GATE_CONTAINER_TAG = Optional.ofNullable(System.getProperty("gateTag")).orElse(REVISION);
    private static final String WORKER_CONTAINER_TAG = Optional.ofNullable(System.getProperty("workerTag")).orElse(REVISION);

    @ClassRule
    public static DockerComposeContainer environment =
            new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
                    .withLocalCompose(true)
                    .withPull(false)
                    .withEnv("nginxTag", NGINX_CONTAINER_TAG)
                    .withEnv("gateTag", GATE_CONTAINER_TAG)
                    .withEnv("workerTag", WORKER_CONTAINER_TAG)
                    .withExposedService("nginx_1", 80)
                    .withExposedService("nginx_1", 8080)
                    .withExposedService("gate_1", 8080)
                    .withExposedService("worker_1", 8080);

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NON_PRIVATE);

    private CloseableHttpClient httpClient;

    @Before
    public void init() {
        httpClient = HttpClientBuilder.create().build();
    }

    @After
    public void teardown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    //TODO: use another http client
    @Test
    public void queryTest() throws InterruptedException {
        int nginxStatusPort = environment.getServicePort("nginx_1", 8080);
        int nginxProxyPort = environment.getServicePort("nginx_1", 80);
        awaitStatus(nginxStatusPort, ContainerUtils.Status.UP);

        String response = given()
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .body(new QueryPayload(null, 10), new Jackson2Mapper((cls, charset) -> MAPPER))
                .when().post("http://127.0.0.1:" + nginxProxyPort + "/first")
                .then().assertThat()
                .statusCode(200)
                .extract().body().asString();
        UUID transactionId = UUID.fromString(response);

        given()
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .body(new QueryPayload(transactionId, 20), new Jackson2Mapper((cls, charset) -> MAPPER))
                .when().post("http://127.0.0.1:" + nginxProxyPort + "/second")
                .then().assertThat()
                .statusCode(200).body(equalTo("30"));
    }

    @RequiredArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class QueryPayload {
        final UUID txId;
        final Integer value;
    }
}