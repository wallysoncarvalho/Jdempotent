package com.trendyol.jdempotent.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field within a request payload to receive the generated idempotency key.
 * 
 * <p>When applied to a field in a request payload object (typically the first parameter 
 * of an {@code @JdempotentResource} method), the Jdempotent framework will automatically 
 * populate this field with the idempotency key that was used for the operation.</p>
 * 
 * <p>This annotation is useful when you need to capture the idempotency key within 
 * the request payload itself, allowing you to:</p>
 * <ul>
 * <li>Return the key as part of the response</li>
 * <li>Log the key for debugging purposes</li>
 * <li>Store the key along with the processed data</li>
 * <li>Track which idempotency key was used for auto-generated scenarios</li>
 * </ul>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>
 * public class CreateOrderRequest {
 *     private String customerId;
 *     private BigDecimal amount;
 *     
 *     {@code @JdempotentIdTarget}
 *     private String idempotencyKey; // Will be populated with the actual key used
 * }
 * 
 * {@code @JdempotentResource}
 * public OrderResponse createOrder(CreateOrderRequest request) {
 *     // request.idempotencyKey now contains the key (generated or provided)
 *     OrderResponse response = processOrder(request);
 *     response.setIdempotencyKey(request.idempotencyKey); // Can return it
 *     return response;
 * }
 * </pre>
 * 
 * <p><strong>Note:</strong> This annotation works on fields within the payload object, 
 * not on the service class or method level.</p>
 * 
 * @see JdempotentResource
 * @see JdempotentId
 * @see JdempotentRequestPayload
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JdempotentIdTarget {
}
