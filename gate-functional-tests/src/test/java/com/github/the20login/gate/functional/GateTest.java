package com.github.the20login.gate.functional;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.the20login.test.utils.GitUtils;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.restassured.internal.mapping.Jackson2Mapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.github.the20login.test.utils.FutureUtils.assertFuture;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;

public class GateTest {
    private static final ByteSequence WORKER_KEY = ByteSequence.from("/services/WORKER/" + UUID.randomUUID(), StandardCharsets.UTF_8);
    private static Slf4jLogConsumer WIREMOCK_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("wiremockContainer"));
    private static Slf4jLogConsumer GATE_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("gateContainer"));
    private static Slf4jLogConsumer ETCD_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("etcdContainer"));

    private static final String REVISION = GitUtils.getRevision();
    private static final String GATE_CONTAINER_TAG = Optional.ofNullable(System.getProperty("gateTag")).orElse(REVISION);

    private static Network NETWORK = Network.newNetwork();

    private static GenericContainer WIREMOCK_CONTAINER = new GenericContainer<>("rodolpheche/wiremock:2.23.2-alpine")
            .withExposedPorts(8080)
            .withLogConsumer(WIREMOCK_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .waitingFor(Wait.forListeningPort());

    private static GenericContainer ETCD_CONTAINER = new GenericContainer("quay.io/coreos/etcd:v3.3.12")
            .withCommand("etcd --listen-client-urls http://0.0.0.0:2379 --advertise-client-urls http://etcd:2379")
            .withExposedPorts(2379)
            .withLogConsumer(ETCD_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .withNetworkAliases("etcd")
            .waitingFor(Wait.forListeningPort());

    private static GenericContainer GATE_CONTAINER = new GenericContainer<>("gate:" + GATE_CONTAINER_TAG)
            .withEnv("advertised_address", "gate:8080")
            .withEnv("etcd_servers", "etcd:2379")
            .withExposedPorts(8080)
            .withLogConsumer(GATE_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .waitingFor(Wait.forListeningPort());

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NON_PRIVATE);

    private WireMock wiremock;
    private Client etcdClient;
    private CloseableHttpClient httpClient;

    @BeforeAll
    static void prepareContainers() {
        ETCD_CONTAINER.start();
        WIREMOCK_CONTAINER.start();
        GATE_CONTAINER.start();
    }

    @AfterAll
    static void shutdownContainers() {
        GATE_CONTAINER.stop();
        ETCD_CONTAINER.stop();
        WIREMOCK_CONTAINER.stop();
    }

    @BeforeEach
    void init() {
        wiremock = new WireMock("127.0.0.1", WIREMOCK_CONTAINER.getMappedPort(8080));
        etcdClient = Client.builder().endpoints("http://127.0.0.1:" + ETCD_CONTAINER.getMappedPort(2379)).build();
        httpClient = HttpClientBuilder.create().build();
    }

    @AfterEach
    void teardown() throws ExecutionException, InterruptedException, IOException {
        if (etcdClient != null) {
            etcdClient.getKVClient().delete(WORKER_KEY).get();
            etcdClient.close();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void queryTest() {
        ByteSequence workerMockAddress = ByteSequence.from("wiremock:8080", StandardCharsets.UTF_8);
        assertFuture(etcdClient.getKVClient().put(WORKER_KEY, workerMockAddress));
        wiremock.register(post(urlEqualTo("/first"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("151")
                )
        );

        String response = given()
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .body(new QueryPayload(null, 10), new Jackson2Mapper((cls, charset) -> MAPPER))
                .when().post("http://127.0.0.1:" + GATE_CONTAINER.getMappedPort(8080) + "/first")
                .then().assertThat()
                .statusCode(200)
                .extract().body().asString();

        UUID transactionId = UUID.fromString(response);

        //TODO: validate body
        wiremock.verifyThat(postRequestedFor(urlEqualTo("/first"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accepts", equalTo("text/plain"))
        );

        wiremock.register(post(urlEqualTo("/second"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("123")
                )
        );

        given()
                .header("Accept", "text/plain")
                .header("Content-type", "application/json")
                .body(new QueryPayload(transactionId, 10), new Jackson2Mapper((cls, charset) -> MAPPER))
                .when().post("http://127.0.0.1:" + GATE_CONTAINER.getMappedPort(8080) + "/second")
                .then().assertThat()
                .statusCode(200).body(CoreMatchers.equalTo("123"));

        //TODO: validate body
        wiremock.verifyThat(postRequestedFor(urlEqualTo("/second"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accepts", equalTo("text/plain"))
        );
    }

    @RequiredArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class QueryPayload {
        final UUID txId;
        final Integer value;
    }
}