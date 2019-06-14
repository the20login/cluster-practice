package com.github.the20login.cluster.gate.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class SumController {
    @Autowired
    private SumService service;
    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(value = "/first", produces = MediaType.TEXT_PLAIN_VALUE)
    public CompletableFuture<String> first(@Valid @RequestBody PayloadWithoutTxId request) throws JsonProcessingException {
        UUID txId = UUID.randomUUID();
        String message = objectMapper.writeValueAsString(new PayloadWithTxId(txId, request.getValue()));
        return service.sendMessage("first", txId, message)
                .thenApply(s -> txId.toString());
    }

    @PostMapping(value = "/second", produces = MediaType.TEXT_PLAIN_VALUE)
    public CompletableFuture<String> second(@Valid @RequestBody PayloadWithTxId request) throws JsonProcessingException {
        String message = objectMapper.writeValueAsString(request);
        return service.sendMessage("second", request.txId, message);
    }

    @Data
    @Valid
    public static class PayloadWithoutTxId {
        @NotNull
        private Integer value;
    }

    @Data
    @Valid
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PayloadWithTxId {
        @NotNull
        private UUID txId;
        @NotNull
        private Integer value;
    }
}
