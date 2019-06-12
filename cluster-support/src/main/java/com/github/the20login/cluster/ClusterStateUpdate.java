package com.github.the20login.cluster;

import lombok.Getter;
import lombok.ToString;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@ToString
public class ClusterStateUpdate {
    private final ClusterState clusterState;
    private final Map<NodeType, Map<UUID, NodeInfo>> updated;
    private final Map<NodeType, Set<UUID>> removed;

    public ClusterStateUpdate(ClusterState clusterState, Map<NodeType, Map<UUID, NodeInfo>> updated, Map<NodeType, Set<UUID>> removed) {
        this.clusterState = clusterState;
        this.updated = updated;
        this.removed = removed;
    }
}
