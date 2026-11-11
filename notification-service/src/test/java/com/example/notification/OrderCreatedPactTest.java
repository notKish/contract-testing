package com.example.notification;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Message consumer side: notification-service's expectations for the "order.created" message.
 *
 * <p>This repo verifies the message contract bi-directionally: order-service publishes
 * docs/asyncapi/order-service-messages.yaml as its provider contract, and PactFlow compares this
 * pact against that document. There is no pact verifier on the provider side.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "order-service-messages", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V4)
class OrderCreatedPactTest {

    private final OrderCreatedNotificationHandler handler = new OrderCreatedNotificationHandler();

    @Pact(consumer = "notification-service")
    V4Pact orderCreatedPact(PactBuilder builder) {
        return builder
                .given("an order is created")
                .expectsToReceiveMessageInteraction("an order created event", message -> message
                        // PactFlow resolves a message interaction to its AsyncAPI operation by
                        // operationId. Without this reference the comparison fails with
                        // "message.references.missing", regardless of the payload. Springwolf derives
                        // the id from channel + action + method name, hence the dots.
                        .reference("AsyncAPI", "operationId", "order.created_send_publish")
                        .withContents(contents -> contents
                                // pact-jvm writes contentType into the metadata itself; metadata is
                                // compared against the AsyncAPI headers schema, which declares it.
                                .withMetadata(Map.of("contentType", "application/json"))
                                .withContent(newJsonBody(body -> {
                                    body.uuid("orderId", UUID.fromString("11111111-2222-3333-4444-555555555555"));
                                    body.uuid("customerId", UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
                                    body.decimalType("total", 42.50);
                                    body.stringType("createdAt", "2026-01-01T00:00:00Z");
                                }).build())))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "orderCreatedPact")
    void theHandlerNotifiesOnOrderCreated(V4Interaction.AsynchronousMessage message) throws Exception {
        // Run the real handler over the message the producer is contractually obliged to send.
        String notification = handler.notifyOrderCreated(message.getContents().getContents().getValue());

        assertTrue(notification.startsWith("Order 11111111-2222-3333-4444-555555555555"),
                "unexpected notification: " + notification);
    }
}
