package aspect.withaspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.AopTestUtils;

import com.trendyol.jdempotent.core.annotation.JdempotentResource;
import com.trendyol.jdempotent.core.constant.CryptographyAlgorithm;
import com.trendyol.jdempotent.core.datasource.InMemoryIdempotentRepository;
import com.trendyol.jdempotent.core.datasource.PayloadConflictException;
import com.trendyol.jdempotent.core.datasource.RequestAlreadyExistsException;
import com.trendyol.jdempotent.core.generator.DefaultKeyGenerator;
import com.trendyol.jdempotent.core.model.IdempotencyKey;
import com.trendyol.jdempotent.core.model.IdempotentIgnorableWrapper;
import com.trendyol.jdempotent.core.model.IdempotentRequestWrapper;

import aspect.core.IdempotentTestPayload;
import aspect.core.TestException;
import aspect.core.TestIdempotentResource;
import aspect.core.TestPayloadWithKey;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {IdempotentAspectIT.class, TestAopContext.class, TestIdempotentResource.class, DefaultKeyGenerator.class, InMemoryIdempotentRepository.class})
public class IdempotentAspectIT {
    
    @Autowired
    private TestIdempotentResource testIdempotentResource;

    @Autowired
    private InMemoryIdempotentRepository idempotentRepository;

    @Autowired
    private DefaultKeyGenerator defaultKeyGenerator;


    @Test
    public void given_aop_context_then_run_with_aop_context() {
        JdempotentResource jdempotentResource = TestIdempotentResource.class.getDeclaredMethods()[0].getAnnotation(JdempotentResource.class);

        assertNotEquals(testIdempotentResource.getClass(), TestIdempotentResource.class);
        assertTrue(AopUtils.isAopProxy(testIdempotentResource));
        assertTrue(AopUtils.isCglibProxy(testIdempotentResource));
        assertNotNull(jdempotentResource);

        assertEquals(AopProxyUtils.ultimateTargetClass(testIdempotentResource), TestIdempotentResource.class);
        assertEquals(AopTestUtils.getTargetObject(testIdempotentResource).getClass(), TestIdempotentResource.class);
        assertEquals(AopTestUtils.getUltimateTargetObject(testIdempotentResource).getClass(), TestIdempotentResource.class);
    }

    @Test
    public void given_new_payload_when_trigger_aspect_then_that_will_be_aviable_in_repository() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put("name", null);
        wrapper.getNonIgnoredFields().put("transactionId", null);

        IdempotencyKey idempotencyKey = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when
        testIdempotentResource.idempotentMethod(test);

        //then
        assertNotNull(idempotentRepository.getRequestResponseWrapper(idempotencyKey));
    }

    @Test
    public void given_new_multiple_payloads_when_trigger_aspect_then_that_will_be_available_in_repository() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentTestPayload test1 = new IdempotentTestPayload();
        IdempotentTestPayload test2 = new IdempotentTestPayload();
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put("name", null);
        wrapper.getNonIgnoredFields().put("transactionId", null);

        IdempotencyKey idempotencyKey = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "TestIdempotentResource", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when
        testIdempotentResource.idempotentMethodWithThreeParameter(test, test1, test2);

        //then
        assertNotNull(idempotentRepository.getRequestResponseWrapper(idempotencyKey));
    }

    @Test
    public void given_invalid_payload_when_trigger_aspect_then_throw_test_exception_and_repository_will_be_empty() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        test.setName("invalid");
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put("name", "invalid");

        IdempotencyKey idempotencyKey = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "TestIdempotentResource", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when & then
        assertThrows(TestException.class, () -> 
            testIdempotentResource.idempotentMethodThrowingARuntimeException(test)
        );
        
        // Verify repository state after exception
        assertNull(idempotentRepository.getRequestResponseWrapper(idempotencyKey));
    }

    @Test
    public void given_new_multiple_payloads_with_multiple_annotations_when_trigger_aspect_then_first_annotated_payload_that_will_be_available_in_repository() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentTestPayload test1 = new IdempotentTestPayload();
        Object test2 = new Object();
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put("name", null);
        wrapper.getNonIgnoredFields().put("transactionId", null);
        IdempotencyKey idempotencyKey = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "TestIdempotentResource", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when
        testIdempotentResource.idempotentMethodWithThreeParamaterAndMultipleJdempotentRequestPayloadAnnotation(test, test1, test2);

        //then
        assertNotNull(idempotentRepository.getRequestResponseWrapper(idempotencyKey));
    }

    @Test
    public void given_no_args_when_trigger_aspect_then_throw_illegal_state_exception() throws NoSuchAlgorithmException {
        //given
        //when & then
        assertThrows(IllegalStateException.class, () -> 
            testIdempotentResource.idempotentMethodWithZeroParamater()
        );
    }

    @Test
    public void given_multiple_args_without_idempotent_request_annotation_when_trigger_aspect_then_throw_illegal_state_exception() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentTestPayload test1 = new IdempotentTestPayload();

        //when & then
        assertThrows(IllegalStateException.class, () -> 
            testIdempotentResource.methodWithTwoParamater(test, test1)
        );
    }

    @Test
    public void given_jdempotent_id_then_args_should_have_idempotency_id() throws NoSuchAlgorithmException {
        //given
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put("name", null);
        wrapper.getNonIgnoredFields().put("transactionId", null);

        IdempotencyKey idempotencyKey = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when
        testIdempotentResource.idempotentMethod(test);

        //then
        assertNotNull(idempotentRepository.getRequestResponseWrapper(idempotencyKey));
    }

    @Test
    public void given_new_payload_as_string_when_trigger_aspect_then_that_will_be_aviable_in_repository() throws NoSuchAlgorithmException {
        //given
        String idempotencyKey = "key";
        IdempotentTestPayload test = new IdempotentTestPayload();
        IdempotentIgnorableWrapper wrapper = new IdempotentIgnorableWrapper();
        wrapper.getNonIgnoredFields().put(idempotencyKey, idempotencyKey);
        IdempotencyKey key = defaultKeyGenerator.generateIdempotentKey(new IdempotentRequestWrapper(wrapper), "", new StringBuilder(), MessageDigest.getInstance(CryptographyAlgorithm.MD5.value()));

        //when
        testIdempotentResource.idempotencyKeyAsString(idempotencyKey);

        //then
        assertNotNull(idempotentRepository.getRequestResponseWrapper(key));
    }

    @Test
    public void given_same_payload_when_called_concurrently_then_increment_should_happen_once() throws InterruptedException {
        // reset static counter
        TestIdempotentResource.inc = 0;

        IdempotentTestPayload payload = new IdempotentTestPayload();
        payload.setName("same");
        payload.setEventId(42L);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    testIdempotentResource.idempotentMethod(payload);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch(Throwable e){
                    if(e.getCause() instanceof RequestAlreadyExistsException){
                        exceptionCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        // synchronize start
        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        // should have incremented only once for the same request
        assertEquals(1, (int) TestIdempotentResource.inc);
        
        // at least one exception should've been thrown
        assertTrue(exceptionCount.get() > 0, "Exception count should be non-negative, got: " + exceptionCount.get());
    }

    @Test
    public void given_different_payload_with_same_key_when_trigger_aspect_then_throw_payload_conflict_exception() throws NoSuchAlgorithmException {
        //given
        String sameIdempotencyKey = "same-key-123";
        
        IdempotentTestPayload firstPayload = new IdempotentTestPayload();
        firstPayload.setName("first");
        firstPayload.setEventId(123L);

        IdempotentTestPayload secondPayload = new IdempotentTestPayload();
        secondPayload.setName("second"); // Different name
        secondPayload.setEventId(456L);   // Different eventId

        // First call should succeed and store the payload
        testIdempotentResource.idempotentMethodWithExplicitId(sameIdempotencyKey, firstPayload);

        // Second call with different payload but same key should throw PayloadConflictException
        assertThrows(PayloadConflictException.class, () -> 
            testIdempotentResource.idempotentMethodWithExplicitId(sameIdempotencyKey, secondPayload)
        );
    }

    @Test
    public void given_same_payload_with_same_key_when_trigger_aspect_then_return_cached_response() throws NoSuchAlgorithmException {
        //given
        String idempotencyKey = "same-key-456";
        
        IdempotentTestPayload payload = new IdempotentTestPayload();
        payload.setName("test");
        payload.setEventId(456L);

        // First call should succeed and store the payload
        IdempotentTestPayload firstResult = testIdempotentResource.idempotentMethodWithExplicitId(idempotencyKey, payload);

        // Second call with same payload and same key should return cached response without throwing exception
        IdempotentTestPayload secondResult = testIdempotentResource.idempotentMethodWithExplicitId(idempotencyKey, payload);

        // Both results should be the same (cached)
        assertEquals(firstResult, secondResult);
        assertEquals("test", secondResult.getName());
    }

    @Test
    public void given_payload_with_idempotency_key_field_when_trigger_aspect_then_use_field_as_key() throws NoSuchAlgorithmException {
        //given
        String idempotencyKey = "field-key-789";
        
        TestPayloadWithKey firstPayload = new TestPayloadWithKey();
        firstPayload.setName("first");
        firstPayload.setIdempotencyKey(idempotencyKey);

        TestPayloadWithKey secondPayload = new TestPayloadWithKey();
        secondPayload.setName("second");
        secondPayload.setIdempotencyKey(idempotencyKey);

        // First call should succeed and store the payload
        testIdempotentResource.idempotentMethodWithPayloadId(firstPayload);

        // Second call with different payload but same idempotency key should throw PayloadConflictException
        assertThrows(PayloadConflictException.class, () -> 
            testIdempotentResource.idempotentMethodWithPayloadId(secondPayload)
        );
    }
}
