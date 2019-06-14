package com.github.the20login.cluster.gate;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

@Slf4j
@Data
@Validated
public class GateProperties {
    @Value("${advertised.address}")
    @NotEmpty
    private String advertisedAddress;
    @Value("${etcd.servers}")
    @NotEmpty
    private Set<String> etcdServers;

    @PostConstruct
    void printUsage() {
        log.info("Use configuration: {}", this);
    }
}
