package com.github.the20login.cluster.etcd;

import com.github.the20login.cluster.*;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

@Slf4j
public class EtcdClusterIntegrationTest {
    private EtcdClusterIntegration cluster;
    private Client client;
    private UUID uuid = UUID.randomUUID();
    private String selfAddress = "127.0.0.1:8080";

    @Rule
    public GenericContainer etcd = new GenericContainer<>("quay.io/coreos/etcd")
            .withCommand("etcd --listen-client-urls http://0.0.0.0:2379 --advertise-client-urls http://127.0.0.1:2379")
            .withExposedPorts(2379);

//    public GenericContainer dslContainer = new GenericContainer(
//            new ImageFromDockerfile().
//    );

    @Before
    public void setUp() throws InterruptedException, ExecutionException {
        client = Client.builder().endpoints("http://localhost:" + etcd.getMappedPort(2379)).build();
        EtcdClusterIntegrationConfiguration config = EtcdClusterIntegrationConfiguration.builder()
                .servicesPath("/services/")
                .advertisedAddress(selfAddress)
                .reconnectInterval(Duration.ofSeconds(1))
                .leaseTtlSeconds(5)
                .build();
        cluster = new EtcdClusterIntegration(config, NodeType.GATE, client);
    }

    @After
    public void tearDown() {
        cluster.close();
        client.close();
    }

    @Test
    public void testSimplePutAndGet() throws ExecutionException, InterruptedException {
        ByteSequence key = ByteSequence.from("/services/" + NodeType.WORKER + "/" + uuid.toString(), UTF_8);
        ByteSequence value = ByteSequence.from("127.0.0.1:8081", UTF_8);
        client.getKVClient().put(key, value).get();

        ClusterStateUpdate update = cluster.getSubject()
                .filter(clusterStateUpdate -> clusterStateUpdate.getClusterState().getNodes().size() == 2)
                .timeout(1, TimeUnit.SECONDS)
                .blockingFirst();
        ClusterState state = update.getClusterState();
        assertEquals(1, state.getNodes().get(NodeType.WORKER).size());
        assertEquals(1, state.getNodes().get(NodeType.GATE).size());
        assertEquals(Health.UP, state.getHealth());
        assertEquals(selfAddress, state.getCurrentNode().getAddress());
        NodeInfo processingNode = state.getNodes().get(NodeType.WORKER).values().stream().findAny().get();

        assertEquals(uuid, processingNode.getId());
        assertEquals(NodeType.WORKER, processingNode.getType());
        assertEquals("127.0.0.1:8081", processingNode.getAddress());


        key = ByteSequence.from("/services/" + NodeType.GATE + "/", UTF_8);
        GetResponse response = client.getKVClient().get(key, GetOption.newBuilder().withPrefix(key).build())
                .get();
        assertEquals(1, response.getCount());
        assertEquals(selfAddress, new String(response.getKvs().get(0).getValue().getBytes(), UTF_8));
    }
}