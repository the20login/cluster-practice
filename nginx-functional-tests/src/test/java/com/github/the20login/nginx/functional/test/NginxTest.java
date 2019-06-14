package com.github.the20login.nginx.functional.test;

import com.github.the20login.test.utils.ContainerUtils;
import com.github.the20login.test.utils.GitUtils;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.github.the20login.test.utils.ContainerUtils.awaitStatus;
import static com.github.the20login.test.utils.FutureUtils.assertFuture;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

public class NginxTest {
    private static final ByteSequence GATE_KEY = ByteSequence.from("/services/GATE/" + UUID.randomUUID(), StandardCharsets.UTF_8);
    private static Slf4jLogConsumer WIREMOCK_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("wiremockContainer"));
    private static Slf4jLogConsumer NGINX_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("nginxContainer"));
    private static Slf4jLogConsumer ETCD_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("etcdContainer"));

    private static final String REVISION = GitUtils.getRevision();
    private static final String NGINX_CONTAINER_TAG = Optional.ofNullable(System.getProperty("gateTag")).orElse(REVISION);

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

    private static GenericContainer NGINX_CONTAINER = new GenericContainer<>("cluster-nginx:" + NGINX_CONTAINER_TAG)
            .withEnv("ETCD_IP", "etcd")
            .withExposedPorts(80, 8080)
            .withLogConsumer(NGINX_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .waitingFor(Wait.forListeningPort());

    private WireMock wiremock;
    private Client etcdClient;

    @BeforeAll
    static void prepareContainers() {
        ETCD_CONTAINER.start();
        WIREMOCK_CONTAINER.start();
        NGINX_CONTAINER.start();
    }

    @AfterAll
    static void shutdownContainers() {
        NETWORK.close();
        NGINX_CONTAINER.stop();
        WIREMOCK_CONTAINER.stop();
        ETCD_CONTAINER.stop();
    }

    @BeforeEach
    public void init() {
        wiremock = new WireMock("127.0.0.1", WIREMOCK_CONTAINER.getMappedPort(8080));
        etcdClient = Client.builder().endpoints("http://127.0.0.1:" + ETCD_CONTAINER.getMappedPort(2379)).build();
    }

    @AfterEach
    public void teardown() throws ExecutionException, InterruptedException {
        etcdClient.getKVClient().delete(GATE_KEY).get();
        awaitStatus(NGINX_CONTAINER.getMappedPort(8080), ContainerUtils.Status.DOWN);
    }

    @Test
    public void queryTest() throws InterruptedException {
        awaitStatus(NGINX_CONTAINER.getMappedPort(8080), ContainerUtils.Status.DOWN);

        given()
                .when().get("http://127.0.0.1:" + NGINX_CONTAINER.getMappedPort(80) + "/endpoint")
                .then().assertThat()
                .statusCode(503);

        ByteSequence workerMockAddress = ByteSequence.from("wiremock:8080", StandardCharsets.UTF_8);
        assertFuture(etcdClient.getKVClient().put(GATE_KEY, workerMockAddress));
        wiremock.register(get(urlEqualTo("/endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("value")
                )
        );
        awaitStatus(NGINX_CONTAINER.getMappedPort(8080), ContainerUtils.Status.UP);

        given()
                .when().get("http://127.0.0.1:" + NGINX_CONTAINER.getMappedPort(80) + "/endpoint")
                .then().assertThat()
                .statusCode(200).body(equalTo("value"));

        wiremock.verifyThat(getRequestedFor(urlEqualTo("/endpoint")));
    }
}