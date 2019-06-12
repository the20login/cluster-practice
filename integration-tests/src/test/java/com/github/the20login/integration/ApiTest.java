package com.github.the20login.integration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.the20login.test.utils.ContainerUtils;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.github.the20login.test.utils.ContainerUtils.awaitStatus;
import static org.junit.Assert.assertEquals;

public class ApiTest {
    @ClassRule
    public static DockerComposeContainer environment =
            new DockerComposeContainer(new File("src/test/resources/docker-compose.yml"))
                    .withLocalCompose(true)
                    .withPull(false)
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
    public void queryTest() throws IOException, InterruptedException {
        int nginxStatusPort = environment.getServicePort("nginx_1", 8080);
        int nginxProxyPort = environment.getServicePort("nginx_1", 80);
        awaitStatus(nginxStatusPort, ContainerUtils.Status.UP);

        HttpPost httpPost = new HttpPost("http://127.0.0.1:" + nginxProxyPort + "/first");
        StringEntity entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(null, 10)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        HttpResponse response = httpClient.execute(httpPost);
        String responseBody = EntityUtils.toString(response.getEntity());
        assertEquals(responseBody, 200, response.getStatusLine().getStatusCode());
        UUID transactionId = UUID.fromString(responseBody);

        httpPost = new HttpPost("http://127.0.0.1:" + nginxProxyPort + "/second");
        entity = new StringEntity(MAPPER.writeValueAsString(new QueryPayload(transactionId, 20)));
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "text/plain");
        httpPost.setHeader("Content-type", "application/json");
        response = httpClient.execute(httpPost);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("30", EntityUtils.toString(response.getEntity()));
    }

    @RequiredArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class QueryPayload {
        final UUID txId;
        final Integer value;
    }
}