package com.example.order;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;

/**
 * Message provider side: verifies the order.created pact published by notification-service against the
 * message order-service actually produces. Pacts and results come from and go to the Pact Broker.
 */
@Provider("order-service-messages")
@PactBroker(
        url = "${pactbroker.url}",
        authentication =
                @PactBrokerAuth(
                        username = "${pactbroker.auth.username}",
                        password = "${pactbroker.auth.password}",
                        token = "${pactbroker.auth.token}"))
class OrderCreatedMessageProviderTest {

    private final OrderCreatedEventPublisher publisher = new OrderCreatedEventPublisher();

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.example.order")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("an order is created")
    void anOrderIsCreated() {
        // The producer builds the event from its arguments; nothing to seed.
    }

    @PactVerifyProvider("an order created event")
    String anOrderCreatedEvent() {
        return publisher.orderCreatedMessage(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
    }
}
