package com.github.the20login.cluster.worker.web;

import com.github.the20login.cluster.worker.processing.SumService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@RestController
public class SumController {
    @Autowired
    private SumService sumService;

    @PostMapping(value = "/first", produces = MediaType.TEXT_PLAIN_VALUE)
    public String first(@Valid @RequestBody Payload request) {
        sumService.first(request.getTxId(), request.getValue());
        return request.value.toString();
    }

    @PostMapping(value = "/second", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> second(@Valid @RequestBody Payload request) {
        Integer sum = sumService.second(request.getTxId(), request.getValue());
        if (sum == null) {
            return new ResponseEntity<>("Missing 'first' request", HttpStatus.FAILED_DEPENDENCY);
        } else {
            return new ResponseEntity<>(sum.toString(), HttpStatus.OK);
        }
    }

    @Data
    @Valid
    public static class Payload {
        @NotNull
        private UUID txId;
        @NotNull
        private Integer value;
    }
}
