package com.github.the20login.cluster.sharding;

import com.github.the20login.cluster.NodeInfo;

import java.util.UUID;

public interface ShardingRouter {
    NodeInfo getNodeForTransaction(UUID txId);
}
