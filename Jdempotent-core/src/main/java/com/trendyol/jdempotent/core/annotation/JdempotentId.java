package com.trendyol.jdempotent.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.trendyol.jdempotent.core.generator.KeyGenerator;

/**
 * Marks a field or parameter that provides the idempotency key for the request.
 * 
 * <p>This annotation is used to explicitly specify which value should be used as the 
 * idempotency key, overriding the default key generation mechanism. The framework 
 * follows a specific priority order when determining the idempotency key:</p>
 * 
 * <ol>
 * <li><strong>Direct parameter annotation:</strong> If a method parameter is annotated 
 * with {@code @JdempotentId}, that parameter's value is used as the key</li>
 * <li><strong>Field within payload:</strong> If a parameter annotated with 
 * {@code @JdempotentRequestPayload} contains a field annotated with {@code @JdempotentId}, 
 * that field's value is used as the key</li>
 * <li><strong>Auto-generated key:</strong> If neither of the above is found, the framework 
 * generates a key based on the request payload content using the configured {@code KeyGenerator} bean (the default is {@code DefaultKeyGenerator}).</li>
 * </ol>
 * 
 * <p><strong>Parameter Usage:</strong></p>
 * <pre>
 * {@code @JdempotentResource}
 * public OrderResponse createOrder({@code @JdempotentId} String orderId, 
 *                                 {@code @RequestBody} CreateOrderRequest request) {
 *     // orderId will be used as the idempotency key
 *     return processOrder(orderId, request);
 * }
 * </pre>
 * 
 * <p><strong>Field Usage within Payload:</strong></p>
 * <pre>
 * public class CreateOrderRequest {
 *     {@code @JdempotentId}
 *     private String transactionId; // This field provides the idempotency key
 *     
 *     private String customerId;
 *     private BigDecimal amount;
 * }
 * 
 * {@code @JdempotentResource}
 * public OrderResponse createOrder({@code @JdempotentRequestPayload} CreateOrderRequest request) {
 *     // request.transactionId will be used as the idempotency key
 *     return processOrder(request);
 * }
 * </pre>
 * 
 * <p><strong>Header-based Idempotency:</strong></p>
 * <pre>
 * {@code @JdempotentResource}
 * public ResponseEntity&lt;EmailResponse&gt; sendEmail(
 *         {@code @JdempotentId @RequestHeader("x-idempotency-key")} String idempotencyKey,
 *         {@code @RequestBody} SendEmailRequest request) {
 *     // The x-idempotency-key header value will be used as the idempotency key
 *     return processEmail(request);
 * }
 * </pre>
 * 
 * <p><strong>Important Notes:</strong></p>
 * <ul>
 * <li>The annotated field or parameter value is converted to String using {@code String.valueOf()}</li>
 * <li>If the value is null, it's treated as null (fallback to auto-generation may occur)</li>
 * <li>Parameter-level annotation takes precedence over field-level annotation</li>
 * <li>Only one {@code @JdempotentId} should be used per idempotent method</li>
 * <li>When used on fields, the containing parameter should be annotated with {@code @JdempotentRequestPayload}</li>
 * </ul>
 * 
 * @see JdempotentResource
 * @see JdempotentRequestPayload
 * @see JdempotentIdTarget
 * @see KeyGenerator
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface JdempotentId {    
}
