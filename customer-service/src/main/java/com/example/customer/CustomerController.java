package com.example.customer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
class CustomerController {

    private static final UUID KNOWN_CUSTOMER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @GetMapping("/{id}")
    ResponseEntity<Customer> getCustomer(@PathVariable UUID id) {
        if (!KNOWN_CUSTOMER_ID.equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(new Customer(id, "Alice Smith", "alice@example.com"));
    }

    record Customer(UUID id, String name, String email) {
    }
}
