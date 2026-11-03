package com.example.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The message consumer: turns an order.created payload into the notification text.
 */
class OrderCreatedNotificationHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    String notifyOrderCreated(byte[] payload) throws Exception {
        JsonNode event = MAPPER.readTree(payload);
        return "Order " + event.get("orderId").asText() + " created for customer "
                + event.get("customerId").asText() + " (total " + event.get("total").asText() + ")";
    }
}
