package aspect.chain;

import aspect.core.IdempotentTestPayload;
import com.trendyol.jdempotent.core.chain.JdempotentDefaultChain;
import com.trendyol.jdempotent.core.chain.JdempotentNoAnnotationChain;
import com.trendyol.jdempotent.core.model.ChainData;
import com.trendyol.jdempotent.core.model.KeyValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
public class JdempotentNoAnnotationChainTest {

    @InjectMocks
    private JdempotentNoAnnotationChain jdempotentNoAnnotationChain;

    @Mock
    private JdempotentDefaultChain jdempotentDefaultChain;

    @BeforeEach
    public void setup(){
        jdempotentNoAnnotationChain.next(jdempotentDefaultChain);
    }

    @Test
    public void should_process_with_no_annotation() throws IllegalAccessException, NoSuchFieldException {
        //Given
        MockData mockData = new MockData();
        ChainData chainData = new ChainData();
        chainData.setArgs(mockData);
        chainData.setDeclaredField(mockData.getClass().getDeclaredField("name"));

        //When
        KeyValuePair process = jdempotentNoAnnotationChain.process(chainData);

        //Then
        assertEquals("name", process.getKey());
        assertEquals(null, process.getValue());
    }


    @Test
    public void should_process_with_another_annotated_property() throws IllegalAccessException, NoSuchFieldException {
        //Given
        IdempotentTestPayload idempotentTestPayload = new IdempotentTestPayload();
        idempotentTestPayload.setEventId(1l);
        ChainData chainData = new ChainData();
        chainData.setArgs(idempotentTestPayload);
        chainData.setDeclaredField(idempotentTestPayload.getClass().getDeclaredField("eventId"));

        //When
        KeyValuePair process = jdempotentNoAnnotationChain.process(chainData);

        //Then
        verify(jdempotentDefaultChain).process(eq(chainData));
    }

    class MockData{
        private String name;
    }
}
