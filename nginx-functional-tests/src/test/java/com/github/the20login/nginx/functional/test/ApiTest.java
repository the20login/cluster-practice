package com.github.the20login.nginx.functional.test;

import com.github.the20login.test.utils.ContainerUtils;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.github.the20login.test.utils.ContainerUtils.awaitStatus;
import static com.github.the20login.test.utils.FutureUtils.assertFuture;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

public class ApiTest {
    private static final ByteSequence GATE_KEY = ByteSequence.from("/services/GATE/" + UUID.randomUUID(), StandardCharsets.UTF_8);
    private static Slf4jLogConsumer WIREMOCK_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("wiremockContainer"));
    private static Slf4jLogConsumer NGINX_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("nginxContainer"));
    private static Slf4jLogConsumer ETCD_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("etcdContainer"));

    @ClassRule
    public static Network NETWORK = Network.newNetwork();

    @ClassRule
    public static GenericContainer wiremockContainer = new GenericContainer<>("rodolpheche/wiremock:2.23.2-alpine")
            .withExposedPorts(8080)
            .withLogConsumer(WIREMOCK_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .waitingFor(Wait.forListeningPort());

    @ClassRule
    public static GenericContainer etcdContainer = new GenericContainer("quay.io/coreos/etcd:v3.3.12")
            .withCommand("etcd --listen-client-urls http://0.0.0.0:2379 --advertise-client-urls http://etcd:2379")
            .withExposedPorts(2379)
            .withLogConsumer(ETCD_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .withNetworkAliases("etcd")
            .waitingFor(Wait.forListeningPort());

    @ClassRule
    public static GenericContainer nginxContainer = new GenericContainer<>("cluster-nginx")
            .withEnv("ETCD_IP", "etcd")
            .withExposedPorts(80, 8080)
            .withLogConsumer(NGINX_LOG_CONSUMER)
            .withNetwork(NETWORK)
            .waitingFor(Wait.forListeningPort());

    private WireMock wiremock;
    private Client etcdClient;

    @Before
    public void init() {
        wiremock = new WireMock("127.0.0.1", wiremockContainer.getMappedPort(8080));
        etcdClient = Client.builder().endpoints("http://127.0.0.1:" + etcdContainer.getMappedPort(2379)).build();
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        etcdClient.getKVClient().delete(GATE_KEY).get();
        awaitStatus(nginxContainer.getMappedPort(8080), ContainerUtils.Status.DOWN);
    }

    //TODO: use another http client
    @Test
    public void queryTest() throws IOException, InterruptedException {
        awaitStatus(nginxContainer.getMappedPort(8080), ContainerUtils.Status.DOWN);

        HttpUriRequest request = new HttpGet("http://127.0.0.1:" + nginxContainer.getMappedPort(80) + "/endpoint");
        HttpResponse response = HttpClientBuilder.create().build().execute(request);
        assertEquals(503, response.getStatusLine().getStatusCode());

        ByteSequence workerMockAddress = ByteSequence.from("wiremock:8080", StandardCharsets.UTF_8);
        assertFuture(etcdClient.getKVClient().put(GATE_KEY, workerMockAddress));
        wiremock.register(get(urlEqualTo("/endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("value")
                )
        );
        awaitStatus(nginxContainer.getMappedPort(8080), ContainerUtils.Status.UP);

        request = new HttpGet("http://127.0.0.1:" + nginxContainer.getMappedPort(80) + "/endpoint");
        response = HttpClientBuilder.create().build().execute(request);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("value", EntityUtils.toString(response.getEntity()));

        wiremock.verifyThat(getRequestedFor(urlEqualTo("/endpoint")));
    }
}