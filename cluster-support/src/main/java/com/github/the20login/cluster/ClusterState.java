package com.github.the20login.cluster;

import lombok.Getter;
import org.pcollections.PMap;

import java.util.UUID;

@Getter
public class ClusterState {
    private final Health health;
    private final PMap<NodeType, PMap<UUID, NodeInfo>> nodes;
    private final NodeInfo currentNode;

    public ClusterState(Health health, PMap<NodeType, PMap<UUID, NodeInfo>> nodes, NodeInfo currentNode) {
        this.health = health;
        this.nodes = nodes;
        this.currentNode = currentNode;
    }
}
