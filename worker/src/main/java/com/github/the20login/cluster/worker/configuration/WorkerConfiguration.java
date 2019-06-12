package com.github.the20login.cluster.worker.configuration;

import com.github.the20login.cluster.ClusterIntegration;
import com.github.the20login.cluster.NodeType;
import com.github.the20login.cluster.etcd.EtcdClusterIntegration;
import com.github.the20login.cluster.etcd.EtcdClusterIntegrationConfiguration;
import com.github.the20login.cluster.sharding.ShardingRouter;
import com.github.the20login.cluster.sharding.ShardingRouterImpl;
import com.github.the20login.cluster.worker.WorkerProperties;
import io.etcd.jetcd.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class WorkerConfiguration {
    @Bean
    Client etcdClient(WorkerProperties properties) {
        String[] servers = properties.getEtcdServers().stream()
                .map(server -> "http://" + server)
                .toArray(String[]::new);
        return Client.builder().endpoints(servers).build();
    }

    @Bean
    @ConfigurationProperties
    WorkerProperties workerProperties() {
        return new WorkerProperties();
    }

    @Bean
    EtcdClusterIntegrationConfiguration clusterConfiguration(WorkerProperties properties) {
        return EtcdClusterIntegrationConfiguration.builder()
                .servicesPath("/services/")
                .advertisedAddress(properties.getAdvertisedAddress())
                .leaseTtlSeconds(5)
                .reconnectInterval(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    ClusterIntegration clusterIntegration(Client etcdClient, EtcdClusterIntegrationConfiguration configuration) {
        return new EtcdClusterIntegration(configuration, NodeType.WORKER, etcdClient);
    }

    @Bean
    ShardingRouter shardingRouter(ClusterIntegration cluster) {
        return new ShardingRouterImpl(cluster);
    }
}
