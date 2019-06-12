package com.github.the20login.cluster.sharding;

import com.github.the20login.cluster.*;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.*;
import java.util.stream.IntStream;

@Slf4j
public class ShardingRouterImpl implements ShardingRouter, AutoCloseable {
    private static final int SHARDS_COUNT = 1000;
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();
    private final ClusterIntegration cluster;

    private volatile Binding binding = Binding.EMPTY;

    private final Disposable subscription;

    public ShardingRouterImpl(ClusterIntegration cluster) {
        this.cluster = cluster;

        subscription = cluster.getSubject().subscribe(this::applyUpdate);
    }

    private void applyUpdate(ClusterStateUpdate clusterUpdate) {
        NodeInfo[] nodesByShard = Arrays.copyOf(binding.nodesByShard, binding.nodesByShard.length);
        PMap<UUID, Set<Integer>> shardsByNode = binding.shardsByNode;

        binding = recalculateShardsBinding(clusterUpdate, nodesByShard, shardsByNode);
    }

    private static Binding recalculateShardsBinding(ClusterStateUpdate clusterUpdate, NodeInfo[] nodesByShard, PMap<UUID, Set<Integer>> shardsByNode) {
        ClusterState clusterState = clusterUpdate.getClusterState();
        PMap<UUID, NodeInfo> processingNodes = clusterState.getNodes().getOrDefault(NodeType.WORKER, HashTreePMap.empty());

        if (processingNodes.isEmpty()) {
            log.debug("No worker nodes, reset binding");
            return Binding.EMPTY;
        }

        if (shardsByNode.isEmpty()) {
            log.debug("No current mapping, recalculate all");
            IntStream.range(0, SHARDS_COUNT)
                    .forEach(shard -> {
                        NodeInfo selectedNode = processingNodes.values().stream()
                                .max(Comparator.comparing(node -> {
                                    long hash = hash(node.getId(), shard);
                                    log.trace("Hash for shard {} and node {} is {]", shard, node);
                                    return hash;
                                }))
                                .get();
                        log.trace("Bind shard {} to {}", shard, selectedNode);
                        nodesByShard[shard] = selectedNode;
                    });
            return new Binding(nodesByShard, shardsByNode);
        }

        Map<UUID, NodeInfo> updatedNodes = clusterUpdate.getUpdated().getOrDefault(NodeType.WORKER, Collections.emptyMap());
        if (!updatedNodes.isEmpty()) {
            log.debug("Current mapping exists, some nodes are updated, recalculate shards for them");
            updatedNodes.values()
                    .forEach(node -> {
                        //for each shard compare updated node to currently used
                        IntStream.range(0, SHARDS_COUNT)
                                .forEach(shard -> {
                                    long updatedNodeHash = hash(node.getId(), shard);
                                    long currentNodeHash = hash(nodesByShard[shard].getId(), shard);
                                    if (updatedNodeHash > currentNodeHash) {
                                        log.trace("Bind shard {} to {}", shard, node);
                                        nodesByShard[shard] = node;
                                    }
                                });
                    });
        }

        Set<UUID> removedNodes = clusterUpdate.getRemoved().getOrDefault(NodeType.WORKER, Collections.emptySet());
        if (!removedNodes.isEmpty()) {
            log.debug("Current mapping exists, some nodes are removed, recalculate shards for them");
            removedNodes.stream()
                    .flatMap(nodeId -> shardsByNode.get(nodeId).stream())
                    .forEach(shard -> {
                        NodeInfo selectedNode = processingNodes.values().stream()
                                .max(Comparator.comparing(node -> hash(node.getId(), shard)))
                                .get();
                        log.trace("Bind shard {} to {}", shard, selectedNode);
                        nodesByShard[shard] = selectedNode;
                    });
        }
        return new Binding(nodesByShard, shardsByNode);
    }

    private NodeInfo getNodeForShard(int shard) {
        return binding.nodesByShard[shard];
    }

    @Override
    public NodeInfo getNodeForTransaction(UUID txId) {
        int shard = Math.abs(txId.hashCode() % SHARDS_COUNT);
        return getNodeForShard(shard);
    }

    @Override
    public void close() throws Exception {
        subscription.dispose();
    }

    private static long hash(UUID nodeId, int shard) {
        return HASH_FUNCTION.newHasher()
                .putLong(nodeId.getMostSignificantBits())
                .putLong(nodeId.getLeastSignificantBits())
                .putInt(shard)
                .hash().asLong();
    }

    private static class Binding {
        private static final Binding EMPTY = new Binding(new NodeInfo[SHARDS_COUNT], HashTreePMap.empty());

        //should be immutable
        private final NodeInfo[] nodesByShard;
        private final PMap<UUID, Set<Integer>> shardsByNode;

        private Binding(NodeInfo[] NodesByShard, PMap<UUID, Set<Integer>> shardsByNode) {
            this.nodesByShard = NodesByShard;
            this.shardsByNode = shardsByNode;
        }
    }
}
