package com.github.the20login.cluster.worker.processing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SumService {
    private Map<UUID, TransactionContext> transactions = new HashMap<>();

    public void first(UUID txId, Integer first) {
        transactions.putIfAbsent(txId, TransactionContext.withFirst(first));
    }

    public Integer second(UUID txId, Integer second) {
        TransactionContext context = transactions.computeIfPresent(txId, (uuid, previous) -> {
            if (previous.isAlreadyProcessed()) {
                return previous;
            } else {
                return previous.withSecond(second);
            }
        });
        if (context == null) {
            return null;
        } else {
            return context.getSum();
        }
    }

    @Getter
    @EqualsAndHashCode
    private static class TransactionContext {
        private final Integer first;
        private final Integer second;
        private final Integer sum;

        private TransactionContext(Integer first) {
            this.first = first;
            this.second = null;
            this.sum = null;
        }

        private TransactionContext(Integer first, Integer second) {
            this.first = first;
            this.second = second;
            this.sum = first + second;
        }

        public boolean isAlreadyProcessed() {
            return second != null;
        }

        public static TransactionContext withFirst(Integer first) {
            return new TransactionContext(first);
        }

        public TransactionContext withSecond(Integer second) {
            return new TransactionContext(this.first, second);
        }
    }
}
