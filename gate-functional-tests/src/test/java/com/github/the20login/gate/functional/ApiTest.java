package com.github.the20login.gate.functional;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.github.the20login.test.utils.FutureUtils.assertFuture;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

public class ApiTest {
    private static final ByteSequence WORKER_KEY = ByteSequence.from("/services/WORKER/" + UUID.randomUUID(), StandardCharsets.UTF_8);
    private static Slf4jLogConsumer WIREMOCK_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("wiremockContainer"));
    private static Slf4jLogConsumer GATE_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("gateContainer"));
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

    @Rule
    public GenericContainer gateContainer = new GenericContainer<>("gate")
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


    @Before
    public void init() {
        wiremock = new WireMock("127.0.0.1", wiremockContainer.getMappedPort(8080));
        etcdClient = Client.builder().endpoints("http://127.0.0.1:" + etcdContainer.getMappedPort(2379)).build();
        httpClient = HttpClientBuilder.create().build();
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException, IOException {
        if (etcdClient != null) {
            etcdClient.getKVClient().delete(WORKER_KEY).get();
            etcdClient.close();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    //TODO: use another http client
    @Test
    public void queryTest() throws IOException, InterruptedException {
        ByteSequence workerMockAddress = ByteSequence.from("wiremock:8080", StandardCharsets.UTF_8);
        assertFuture(etcdClient.getKVClient().put(WORKER_KEY, workerMockAddress));
        wiremock.register(post(urlEqualTo("/first"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("151")
                )
        );

        HttpPost httpPost = new HttpPost("http://127.0.0.1:" + gateContainer.getMappedPort(8080) + "/first");
        StringEntity entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(null, 10)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        HttpResponse response = httpClient.execute(httpPost);
        assertEquals(200, response.getStatusLine().getStatusCode());
        UUID transactionId = UUID.fromString(EntityUtils.toString(response.getEntity()));

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

        httpPost = new HttpPost("http://127.0.0.1:" + gateContainer.getMappedPort(8080) + "/second");
        entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(transactionId, 10)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        response = httpClient.execute(httpPost);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("123", EntityUtils.toString(response.getEntity()));

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