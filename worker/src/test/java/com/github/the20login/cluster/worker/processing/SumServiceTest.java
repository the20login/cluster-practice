package com.github.the20login.cluster.worker.processing;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SumServiceTest {
    private SumService sumService;

    @Before
    public void init() {
        sumService = new SumService();
    }

    @Test
    public void positive() {
        UUID transactionId = UUID.randomUUID();
        sumService.first(transactionId, 10);
        Integer sum = sumService.second(transactionId, 20);
        assertEquals(30, sum.intValue());
    }
}