package aspect.chain;

import aspect.core.IdempotentTestPayload;
import com.trendyol.jdempotent.core.chain.JdempotentDefaultChain;
import com.trendyol.jdempotent.core.chain.JdempotentIgnoreAnnotationChain;
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
public class JdempotentIgnoreAnnotationChainTest {

    @InjectMocks
    private JdempotentIgnoreAnnotationChain jdempotentIgnoreAnnotationChain;

    @Mock
    private JdempotentDefaultChain jdempotentDefaultChain;

    @BeforeEach
    public void setup(){
        jdempotentIgnoreAnnotationChain.next(jdempotentDefaultChain);
    }

    @Test
    public void should_process() throws IllegalAccessException, NoSuchFieldException {
        //Given
        IdempotentTestPayload idempotentTestPayload = new IdempotentTestPayload();
        idempotentTestPayload.setAge(1l);
        ChainData chainData = new ChainData();
        chainData.setArgs(idempotentTestPayload);
        chainData.setDeclaredField(idempotentTestPayload.getClass().getDeclaredField("age"));

        //When
        KeyValuePair process = jdempotentIgnoreAnnotationChain.process(chainData);

        //Then
        assertEquals(null, process.getKey());
        assertEquals(null, process.getValue());
    }

    @Test
    public void should_not_process_when_given_another_annotated_field() throws IllegalAccessException, NoSuchFieldException {
        //Given
        IdempotentTestPayload idempotentTestPayload = new IdempotentTestPayload();
        idempotentTestPayload.setName("name");
        ChainData chainData = new ChainData();
        chainData.setArgs(idempotentTestPayload);
        chainData.setDeclaredField(idempotentTestPayload.getClass().getDeclaredField("name"));

        //When
        jdempotentIgnoreAnnotationChain.process(chainData);

        //Then
        verify(jdempotentDefaultChain).process(eq(chainData));
    }
}
