package jdempotent.postgres.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;

import jdempotent.postgres.support.AbstractPostgresStarterIntegrationTest;

@SpringBootTest(classes = TestHttpApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test-http.properties")
class PostgresStarterHttpIT extends AbstractPostgresStarterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpProcessingTracker tracker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpTracker() {
        tracker.reset();
    }

    @Test
    void should_process_request_once_and_return_cached_response_on_subsequent_calls() throws Exception {
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("http-123", "payload");
        String expectedResponse = "{\"idempotencyKey\":\"http-123\",\"result\":\"processed-payload\"}";

        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(expectedResponse));

        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(expectedResponse));

        assertEquals(1, tracker.getInvocationCount("http-123"));
    }

    @Test
    void should_fail_when_payload_differs_for_same_idempotency_key() throws Exception {
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("http-456", "payload-a");
        
        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        TestHttpController.RequestPayload conflicting = new TestHttpController.RequestPayload("http-456", "payload-b");

        // Expect the PayloadConflictException to be thrown and wrapped in a ServletException
        Exception exception = assertThrows(Exception.class, () -> 
            mockMvc.perform(post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(conflicting))));
        
        // Check that the cause is PayloadConflictException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.trendyol.jdempotent.core.datasource.PayloadConflictException)) {
            cause = cause.getCause();
        }
        assertEquals(com.trendyol.jdempotent.core.datasource.PayloadConflictException.class, 
            cause != null ? cause.getClass() : null);

        assertEquals(1, tracker.getInvocationCount("http-456"));
    }

    @Test
    void should_track_multiple_different_keys() throws Exception {
        // Test multiple different idempotency keys
        TestHttpController.RequestPayload payload1 = new TestHttpController.RequestPayload("multi-1", "data-1");
        TestHttpController.RequestPayload payload2 = new TestHttpController.RequestPayload("multi-2", "data-2");
        TestHttpController.RequestPayload payload3 = new TestHttpController.RequestPayload("multi-3", "data-3");

        // Process each payload
        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload3)))
                .andExpect(status().isOk());

        // Each key should have been invoked once
        assertEquals(1, tracker.getInvocationCount("multi-1"));
        assertEquals(1, tracker.getInvocationCount("multi-2"));
        assertEquals(1, tracker.getInvocationCount("multi-3"));
        assertEquals(3, tracker.getTotalInvocationCount());
    }

    @Test
    void should_handle_error_endpoint_without_caching_exceptions() throws Exception {
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("error-123", "error-data");

        // First call should throw exception
        assertThrows(Exception.class, () -> 
            mockMvc.perform(post("/process-error")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))));

        // Second call with same payload should also throw exception and invoke method again
        // because exceptions are not cached - the key is removed when an exception occurs
        assertThrows(Exception.class, () -> 
            mockMvc.perform(post("/process-error")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload))));

        // Should be invoked twice because exceptions are not cached
        assertEquals(2, tracker.getInvocationCount("error-123"));
    }

    @Test
    void should_process_with_key_header_idempotently() throws Exception {
        String idempotencyKey = "header-key-123";
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("key-123", "key-data");
        String expectedResponse = "{\"idempotencyKey\":\"header-key-123\",\"result\":\"processed-key-data\"}";

        // First call with idempotency key in header
        mockMvc.perform(post("/process-with-key")
                .header("x-idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(expectedResponse));

        // Second call with same header key and payload should return cached response
        mockMvc.perform(post("/process-with-key")
                .header("x-idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(expectedResponse));

        // Should only be invoked once due to idempotency (using payload key for tracking)
        assertEquals(1, tracker.getInvocationCount(idempotencyKey));
    }

    @Test
    void should_handle_missing_idempotency_header() throws Exception {
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("key-789", "key-data");

        // Call without x-idempotency-key header should fail
        mockMvc.perform(post("/process-with-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is4xxClientError());

        // Should not be invoked at all
        assertEquals(0, tracker.getInvocationCount("key-789"));
    }
}
