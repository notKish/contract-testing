package com.example.customer;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
class CustomerController {

  private static final UUID KNOWN_CUSTOMER_ID =
      UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

  @GetMapping("/{id}")
  ResponseEntity<Customer> getCustomer(@PathVariable UUID id) {
    if (!KNOWN_CUSTOMER_ID.equals(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.ok(new Customer(id, "Alice Smith", "alice@example.com"));
  }

  /**
   * Query-parameter endpoint: the consumer pacts match {@code status}/{@code page}/{@code size}
   * with regular expressions and expect an {@code X-Request-Id} response header.
   */
  @GetMapping
  CustomerPage listCustomers(
      @RequestParam(defaultValue = "active") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      HttpServletResponse response) {
    response.setHeader("X-Request-Id", UUID.randomUUID().toString());
    List<Customer> items =
        List.of(new Customer(KNOWN_CUSTOMER_ID, "Alice Smith", "alice@example.com"));
    return new CustomerPage(items, page, size, items.size());
  }

  /**
   * POST endpoint: {@code name} is required (blank is rejected with 400), {@code email} and
   * {@code tags} are optional, which the consumer pacts express as "field absent" vs "field present".
   */
  @PostMapping
  ResponseEntity<?> createCustomer(@RequestBody CreateCustomerRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
    }
    UUID id = UUID.randomUUID();
    Customer customer = new Customer(id, request.name(), request.email());
    return ResponseEntity.created(URI.create("/customers/" + id)).body(customer);
  }

  record Customer(UUID id, String name, String email) {}

  record CustomerPage(List<Customer> items, int page, int size, int total) {}

  record CreateCustomerRequest(String name, String email, List<String> tags) {}
}
