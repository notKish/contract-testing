package com.example.order;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the payload of the "order.created" Kafka message. @PactVerifyProvider in
 * OrderCreatedMessageProviderTest runs this exact method, so notification-service's
 * expectations are checked against the real producer output.
 */
class OrderCreatedEventPublisher {

    static final String TOPIC = "order.created";

    String orderCreatedMessage(UUID orderId, UUID customerId) {
        ObjectNode event = JsonNodeFactory.instance.objectNode();
        event.put("orderId", orderId.toString());
        event.put("customerId", customerId.toString());
        event.put("total", 42.50);
        event.put("createdAt", Instant.parse("2026-01-01T00:00:00Z").toString());
        return event.toString();
    }
}
