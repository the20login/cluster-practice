package com.github.the20login.cluster.worker.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.the20login.cluster.worker.processing.SumService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.*;
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
                .accept(MediaType.TEXT_PLAIN_VALUE)
        ).andExpect(status().is(400));

        verifyZeroInteractions(sumService);
    }

    @Test
    public void first_positive() throws Exception {
        mockMvc.perform(post("/first")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.displayName())
                .content(objectMapper.writeValueAsString(new RequestPayload(UUID.randomUUID(), 10))))
                .andExpect(status().is(200))
                .andExpect(content().string("10"));

        verify(sumService, only()).first(any(), eq(10));
    }

    @Test
    public void second_missingField() throws Exception {
        mockMvc.perform(post("/second")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .content(objectMapper.writeValueAsBytes(new RequestPayload(UUID.randomUUID(), null)))
        ).andExpect(status().is(400));

        verifyZeroInteractions(sumService);
    }

    @Test
    public void second_positive() throws Exception {
        when(sumService.second(any(), eq(10))).thenReturn(20);

        mockMvc.perform(post("/second")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.displayName())
                .content(objectMapper.writeValueAsString(new RequestPayload(UUID.randomUUID(), 10))))
                .andExpect(status().is(200))
                .andExpect(content().string("20"));

        verify(sumService, only()).second(any(), eq(10));
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