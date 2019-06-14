package com.github.the20login.cluster.gate.configuration;

import com.github.the20login.cluster.ClusterIntegration;
import com.github.the20login.cluster.NodeType;
import com.github.the20login.cluster.etcd.EtcdClusterIntegration;
import com.github.the20login.cluster.etcd.EtcdClusterIntegrationConfiguration;
import com.github.the20login.cluster.gate.GateProperties;
import com.github.the20login.cluster.gate.web.SumService;
import com.github.the20login.cluster.sharding.ShardingRouter;
import com.github.the20login.cluster.sharding.ShardingRouterImpl;
import io.etcd.jetcd.Client;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class GateConfiguration {
    @Bean
    WebClient webClient() {
        return WebClient.create();
    }

    @Bean
    SumService sumService(WebClient webClient, ShardingRouter shardingRouter) {
        return new SumService(webClient, shardingRouter);
    }

    @Bean
    @ConfigurationProperties
    GateProperties gateProperties() {
        return new GateProperties();
    }

    @Bean
    Client etcdClient(GateProperties configuration) {
        String[] servers = configuration.getEtcdServers().stream()
                .map(server -> "http://" + server)
                .toArray(String[]::new);
        return Client.builder().endpoints(servers).build();
    }

    @Bean
    EtcdClusterIntegrationConfiguration clusterConfiguration(GateProperties configuration) {
        return EtcdClusterIntegrationConfiguration.builder()
                .servicesPath("/services/")
                .advertisedAddress(configuration.getAdvertisedAddress())
                .leaseTtlSeconds(5)
                .reconnectInterval(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    ClusterIntegration clusterIntegration(Client etcdClient, EtcdClusterIntegrationConfiguration configuration) {
        return new EtcdClusterIntegration(configuration, NodeType.GATE, etcdClient);
    }

    @Bean
    ShardingRouter shardingRouter(ClusterIntegration cluster) {
        return new ShardingRouterImpl(cluster);
    }
}
