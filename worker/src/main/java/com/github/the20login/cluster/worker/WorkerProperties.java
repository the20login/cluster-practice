package com.github.the20login.cluster.worker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

@Slf4j
@Data
public class WorkerProperties {
    @NotEmpty
    @Value("${advertised.address}")
    private String advertisedAddress;
    @NotEmpty
    @Value("${etcd.servers}")
    private Set<String> etcdServers;

    @PostConstruct
    void printUsage() {
        log.info("Use configuration: {}", this);
    }
}
