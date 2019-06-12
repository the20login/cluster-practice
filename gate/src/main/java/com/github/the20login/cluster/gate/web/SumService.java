package com.github.the20login.cluster.gate.web;

import com.github.the20login.cluster.NodeInfo;
import com.github.the20login.cluster.sharding.ShardingRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class SumService {
    private final WebClient webClient;
    private final ShardingRouter shardingRouter;

    public SumService(WebClient webClient, ShardingRouter shardingRouter) {
        this.webClient = webClient;
        this.shardingRouter = shardingRouter;
    }

    public CompletableFuture<String> sendMessage(String url, UUID txId, String message) {
        NodeInfo node = shardingRouter.getNodeForTransaction(txId);

        if (node == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Unable to retrieve node"));
            return future;
        }

        return webClient
                .post()
                .uri("http://" + node.getAddress() + "/{url}", url)// can't use placeholder for address because of escaping
                .syncBody(message)
                .header("Content-Type", "application/json")
                .header("Accepts", "text/plain")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(1))
                .doOnError(e -> log.error("Query failed, node: " + node, e))
                .toFuture();
    }
}
