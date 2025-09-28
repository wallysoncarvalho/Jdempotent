package jdempotent.postgres.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void shouldProcessRequestOnceAndReturnCachedResponseOnSubsequentCalls() throws Exception {
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

        assertThat(tracker.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void shouldFailWhenPayloadDiffersForSameIdempotencyKey() throws Exception {
        TestHttpController.RequestPayload payload = new TestHttpController.RequestPayload("http-456", "payload-a");
        
        mockMvc.perform(post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        TestHttpController.RequestPayload conflicting = new TestHttpController.RequestPayload("http-456", "payload-b");

        // Expect the PayloadConflictException to be thrown and wrapped in a ServletException
        assertThatThrownBy(() -> 
            mockMvc.perform(post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(conflicting))))
                .hasCauseInstanceOf(com.trendyol.jdempotent.core.datasource.PayloadConflictException.class);

        assertThat(tracker.getInvocationCount()).isEqualTo(1);
    }
}
