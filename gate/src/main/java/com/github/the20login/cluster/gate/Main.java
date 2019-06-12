package com.github.the20login.cluster.gate;

import com.github.the20login.cluster.ClusterIntegration;
import com.github.the20login.cluster.NodeType;
import com.github.the20login.cluster.etcd.EtcdClusterIntegration;
import com.github.the20login.cluster.etcd.EtcdClusterIntegrationConfiguration;
import com.github.the20login.cluster.gate.web.SumService;
import com.github.the20login.cluster.sharding.ShardingRouter;
import com.github.the20login.cluster.sharding.ShardingRouterImpl;
import io.etcd.jetcd.Client;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@SpringBootApplication
public class Main {
    public static void main(String... args) {
        SpringApplication.run(Main.class, args);
    }

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
    GateConfiguration gateConfiguration() {
        return new GateConfiguration();
    }

    @Bean
    Client etcdClient(GateConfiguration configuration) {
        String[] servers = configuration.getEtcdServers().stream()
                .map(server -> "http://" + server)
                .toArray(String[]::new);
        return Client.builder().endpoints(servers).build();
    }

    @Bean
    EtcdClusterIntegrationConfiguration clusterConfiguration(GateConfiguration configuration) {
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
