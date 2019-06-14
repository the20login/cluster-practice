package com.github.the20login.cluster.sharding;

import com.github.the20login.cluster.*;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ShardingRouterImplTest {
    private UUID txId = UUID.randomUUID();
    private ClusterIntegration cluster = Mockito.mock(ClusterIntegration.class);
    private PMap<NodeType, PMap<UUID, NodeInfo>> nodes = HashTreePMap.empty();

    @BeforeEach
    void init() {
        Mockito.reset(cluster);
        nodes = HashTreePMap.empty();
    }

    @Test
    void emptyCluster() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        assertNull(sharding.getNodeForTransaction(txId));
    }

    @Test
    void addNewProcessingNode() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        NodeInfo node = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8080");
        subject.onNext(addNode(node));

        assertEquals(node, sharding.getNodeForTransaction(txId));
    }

    @Test
    public void addNewGateNode() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        NodeInfo node = new NodeInfo(UUID.randomUUID(), NodeType.GATE, "127.0.0.1:8080");
        subject.onNext(addNode(node));

        //gate nodes should be ignored
        assertNull(sharding.getNodeForTransaction(txId));
    }

    @Test
    public void removeNewGateNode() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        NodeInfo gateNode = new NodeInfo(UUID.randomUUID(), NodeType.GATE, "127.0.0.1:8080");
        subject.onNext(addNode(gateNode));
        NodeInfo processingNode = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8081");
        subject.onNext(addNode(processingNode));

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        assertEquals(processingNode, sharding.getNodeForTransaction(txId));

        subject.onNext(removeNode(gateNode));

        //gate nodes should be ignored
        assertEquals(processingNode, sharding.getNodeForTransaction(txId));
    }

    @Test
    public void removeProcessingNode_noNodesLeft() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        NodeInfo node = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8080");
        subject.onNext(addNode(node));

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        assertEquals(node, sharding.getNodeForTransaction(txId));

        subject.onNext(removeNode(node));

        //gate nodes should be ignored
        assertNull(sharding.getNodeForTransaction(txId));
    }

    @Test
    public void removeProcessingNode_oneProcessingLeft() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        NodeInfo node = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8080");
        subject.onNext(addNode(node));

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        assertEquals(node, sharding.getNodeForTransaction(txId));

        subject.onNext(removeNode(node));

        //gate nodes should be ignored
        assertNull(sharding.getNodeForTransaction(txId));
    }

    @Test
    public void removeProcessingNode_gateNodeLeft() {
        Subject<ClusterStateUpdate> subject = BehaviorSubject.create();
        Mockito.when(cluster.getSubject()).thenReturn(subject);

        ShardingRouter sharding = new ShardingRouterImpl(cluster);

        //add after initialization, so that resharding was called twice
        NodeInfo node1 = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8080");
        subject.onNext(addNode(node1));
        NodeInfo node2 = new NodeInfo(UUID.randomUUID(), NodeType.WORKER, "127.0.0.1:8081");
        subject.onNext(addNode(node2));

        //some node returned, we don't care which one
        NodeInfo nodeToRemove = sharding.getNodeForTransaction(txId);
        assertThat(nodeToRemove, anyOf(equalTo(node1), equalTo(node2)));
        //then remove this node
        subject.onNext(removeNode(nodeToRemove));

        //other node should be returned
        NodeInfo survivor = nodeToRemove.equals(node1) ? node2 : node1;
        assertEquals(survivor, sharding.getNodeForTransaction(txId));
        assertNotEquals(nodeToRemove, survivor);
    }

    private ClusterStateUpdate addNode(NodeInfo node) {
        PMap<UUID, NodeInfo> typedNodes = nodes.getOrDefault(node.getType(), HashTreePMap.empty());
        typedNodes = typedNodes.plus(node.getId(), node);
        nodes = nodes.plus(node.getType(), typedNodes);

        Map<NodeType, Map<UUID, NodeInfo>> updated = Collections.singletonMap(node.getType(), Collections.singletonMap(node.getId(), node));
        ClusterState state = new ClusterState(Health.UP, nodes, null);
        return new ClusterStateUpdate(state, updated, Collections.emptyMap());
    }

    private ClusterStateUpdate removeNode(NodeInfo node) {
        PMap<UUID, NodeInfo> typedNodes = nodes.get(node.getType());
        typedNodes = typedNodes.minus(node.getId());
        if (typedNodes.isEmpty()) {
            nodes = nodes.minus(node.getType());
        } else {
            nodes = nodes.plus(node.getType(), typedNodes);
        }

        Map<NodeType, Set<UUID>> removed = Collections.singletonMap(node.getType(), Collections.singleton(node.getId()));
        ClusterState state = new ClusterState(Health.UP, nodes, null);
        return new ClusterStateUpdate(state, Collections.emptyMap(), removed);
    }
}