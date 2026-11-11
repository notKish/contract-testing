package com.example.order;

import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import io.github.springwolf.bindings.kafka.annotations.KafkaAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;

/**
 * Publishes the "order.created" message. The payload schema in
 * docs/asyncapi/order-service-messages.yaml is generated from {@link OrderCreatedEvent} by Springwolf;
 * the annotations below supply the channel, operation and headers it cannot infer.
 */
@Component
class OrderCreatedEventPublisher {

    static final String TOPIC = "order.created";
    static final String CONTENT_TYPE_HEADER = "contentType";

    private final KafkaTemplate<String, OrderCreatedEvent> kafka;

    OrderCreatedEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafka) {
        this.kafka = kafka;
    }

    OrderCreatedEvent orderCreatedEvent(UUID orderId, UUID customerId) {
        return new OrderCreatedEvent(orderId, customerId, 42.50, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = TOPIC,
                            description = "An order was created",
                            headers =
                                    @AsyncOperation.Headers(
                                            values =
                                                    @AsyncOperation.Headers.Header(
                                                            name = "contentType",
                                                            description = "The message content type"))))
    @KafkaAsyncOperationBinding
    void publish(
            @Payload
                    @AsyncMessage(name = "orderCreated", title = "OrderCreatedEvent", contentType = "application/json")
                    OrderCreatedEvent event) {
        ProducerRecord<String, OrderCreatedEvent> record =
                new ProducerRecord<>(TOPIC, event.orderId().toString(), event);
        // Declared as metadata by the pact; the document's headers schema is what checks it.
        record.headers().add(CONTENT_TYPE_HEADER, "application/json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        kafka.send(record);
    }
}
