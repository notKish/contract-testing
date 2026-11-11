package com.example.order;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trigger for the "order.created" event. Drift calls this to make the producer publish, so the
 * message it captures is produced by the real code path rather than assembled in a test.
 */
@RestController
class OrderController {

    private final OrderCreatedEventPublisher publisher;

    OrderController(OrderCreatedEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/orders")
    ResponseEntity<Void> createOrder(@RequestBody CreateOrder create) {
        UUID orderId = create.orderId() != null ? create.orderId() : UUID.randomUUID();
        publisher.publish(publisher.orderCreatedEvent(orderId, create.customerId()));
        return ResponseEntity.accepted().build();
    }

    record CreateOrder(UUID orderId, UUID customerId) {}
}
