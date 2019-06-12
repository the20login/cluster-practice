package com.github.the20login.cluster;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
public class NodeInfo {
    private final UUID Id;
    private final NodeType type;
    private final String address;

    public NodeInfo(UUID id, NodeType type, String address) {
        Id = id;
        this.type = type;
        this.address = address;
    }
}
