package com.github.the20login.cluster.gate.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SumController.class)
public class SumControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SumService sumService;

    @Test
    public void first_missingField() throws Exception {
        mockMvc.perform(post("/first")
                .content(objectMapper.writeValueAsBytes(new RequestPayload(UUID.randomUUID(), null)))
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(status().is(400));

        verifyZeroInteractions(sumService);
    }

    @Test
    public void first_positive() throws Exception {
        when(sumService.sendMessage(eq("first"), any(), any())).thenReturn(CompletableFuture.completedFuture("10"));

        MvcResult mvcResult = mockMvc.perform(post("/first")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.displayName())
                .content(objectMapper.writeValueAsString(new RequestPayload(UUID.randomUUID(), 10))))
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().is(200))
                .andExpect(content().string(not(isEmptyString())));

        verify(sumService, only()).sendMessage(eq("first"), any(), any());
    }

    @Test
    public void second_missingField() throws Exception {
        mockMvc.perform(post("/second")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .content(objectMapper.writeValueAsBytes(new RequestPayload(UUID.randomUUID(), null))))
                .andExpect(status().is(400));

        verifyZeroInteractions(sumService);
    }

    @Test
    public void second_positive() throws Exception {
        when(sumService.sendMessage(eq("second"), any(), any())).thenReturn(CompletableFuture.completedFuture("20"));

        MvcResult mvcResult = mockMvc.perform(post("/second")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.displayName())
                .content(objectMapper.writeValueAsString(new RequestPayload(UUID.randomUUID(), 10))))
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().is(200))
                .andExpect(content().string("20"));

        verify(sumService, only()).sendMessage(eq("second"), any(), any());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @RequiredArgsConstructor
    private static class RequestPayload {
        @JsonProperty
        final UUID txId;
        @JsonProperty
        final Integer value;
    }
}