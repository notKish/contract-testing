package com.example.order;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload of the "order.created" Kafka message. Springwolf generates its JSON Schema from this record,
 * so the field types and {@code requiredMode} are what the AsyncAPI document describes.
 *
 * <p>{@code createdAt} is an {@link Instant} but must reach the wire as an RFC 3339 string: Spring
 * Kafka's default mapper writes dates as epoch numbers, which would violate the {@code date-time}
 * format in the contract. Drift catches that if this annotation is dropped.
 */
record OrderCreatedEvent(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID customerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Order total, in the store currency.")
                Double total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonFormat(shape = JsonFormat.Shape.STRING)
                Instant createdAt) {}
