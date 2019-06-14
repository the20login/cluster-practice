package com.github.the20login.worker.functional;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.the20login.test.utils.GitUtils;
import io.restassured.internal.mapping.Jackson2Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

@Slf4j
public class WorkerTest {
    private static Slf4jLogConsumer WORKER_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("workerContainer"));

    private static final String REVISION = GitUtils.getRevision();
    private static final String WORKER_CONTAINER_TAG = Optional.ofNullable(System.getProperty("workerTag")).orElse(REVISION);

    private static Network NETWORK = Network.newNetwork();

    private static GenericContainer ETCD_CONTAINER = new GenericContainer("quay.io/coreos/etcd:v3.3.12")
            .withCommand("etcd --listen-client-urls http://0.0.0.0:2379 --advertise-client-urls http://etcd:2379")
            .withNetworkAliases("etcd")
            .withNetwork(NETWORK)
            .withExposedPorts(2379);

    private static GenericContainer WORKER_CONTAINER = new GenericContainer<>("worker:" + WORKER_CONTAINER_TAG)
            .withEnv("advertised_address", "worker:8080")
            .withEnv("etcd_servers", "etcd:2379")
            .withLogConsumer(WORKER_LOG_CONSUMER)
            .withNetworkAliases("worker")
            .withNetwork(NETWORK)
            .withExposedPorts(8080);

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NON_PRIVATE);

    private CloseableHttpClient httpClient;

    @BeforeAll
    static void prepareContainers() {
        ETCD_CONTAINER.start();
        WORKER_CONTAINER.start();
    }

    @AfterAll
    static void shutdownContainers() {
        WORKER_CONTAINER.stop();
        ETCD_CONTAINER.stop();
        NETWORK.close();
    }

    @BeforeEach
    public void init() {
        httpClient = HttpClientBuilder.create().build();
    }

    @AfterEach
    public void teardown() throws IOException {
        httpClient.close();
    }

    @Test
    public void transactionTest() {
        UUID transactionId = UUID.randomUUID();

        given()
                .contentType("application/json")
                .body(new QueryPayload(transactionId, 10), new Jackson2Mapper((cls, charset) -> MAPPER))
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .when().post("http://127.0.0.1:" + WORKER_CONTAINER.getMappedPort(8080) + "/first")
                .then().assertThat()
                .statusCode(200).body(equalTo("10"));

        given()
                .contentType("application/json")
                .body(new QueryPayload(transactionId, 20), new Jackson2Mapper((cls, charset) -> MAPPER))
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .when().post("http://127.0.0.1:" + WORKER_CONTAINER.getMappedPort(8080) + "/second")
                .then().assertThat()
                .statusCode(200).body(equalTo("30"));
    }

    @RequiredArgsConstructor
    private static class QueryPayload {
        final UUID txId;
        final Integer value;
    }
}