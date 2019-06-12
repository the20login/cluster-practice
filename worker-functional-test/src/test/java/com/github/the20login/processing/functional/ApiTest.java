package com.github.the20login.processing.functional;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
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

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ApiTest {
    private static Slf4jLogConsumer WORKER_LOG_CONSUMER = new Slf4jLogConsumer(LoggerFactory.getLogger("workerContainer"));

    @ClassRule
    public static Network network = Network.newNetwork();

    @ClassRule
    public static GenericContainer etcd = new GenericContainer("quay.io/coreos/etcd:v3.3.12")
            .withCommand("etcd --listen-client-urls http://0.0.0.0:2379 --advertise-client-urls http://etcd:2379")
            .withNetworkAliases("etcd")
            .withNetwork(network)
            .withExposedPorts(2379);

    @Rule
    public GenericContainer worker = new GenericContainer<>("worker")
            .withEnv("advertised_address", "worker:8080")
            .withEnv("etcd_servers", "etcd:2379")
            .withLogConsumer(WORKER_LOG_CONSUMER)
            .withNetworkAliases("worker")
            .withNetwork(network)
            .withExposedPorts(8080);

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NON_PRIVATE);

    private CloseableHttpClient httpClient;

    @Before
    public void init() {
        httpClient = HttpClientBuilder.create().build();
    }

    @After
    public void teardown() throws IOException {
        httpClient.close();
    }

    //TODO: use another http client
    @Test
    public void transactionTest() throws IOException, InterruptedException {
        UUID transactionId = UUID.randomUUID();

        HttpPost httpPost = new HttpPost("http://127.0.0.1:" + worker.getMappedPort(8080) + "/first");
        StringEntity entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(transactionId, 10)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        HttpResponse response = httpClient.execute(httpPost);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("10", EntityUtils.toString(response.getEntity()));

        httpPost = new HttpPost("http://127.0.0.1:" + worker.getMappedPort(8080) + "/second");
        entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(transactionId, 10)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        response = httpClient.execute(httpPost);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("20", EntityUtils.toString(response.getEntity()));
    }

    @RequiredArgsConstructor
    private static class QueryPayload {
        final UUID txId;
        final Integer value;
    }
}