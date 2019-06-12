package com.github.the20login.cluster.etcd;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

@Getter
@Builder
public class EtcdClusterIntegrationConfiguration {
    private final String servicesPath;
    private final String advertisedAddress;
    private final long leaseTtlSeconds;
    private final Duration reconnectInterval;
}

