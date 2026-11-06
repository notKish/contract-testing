package com.example.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for customer-service. The pacts in this module are the contract
 * this class has to keep
 * honouring.
 */
@Component
class CustomerClient {

  /**
   * Sent as X-Api-Key; the pact matches it by regular expression, not by value.
   */
  static final String API_KEY = "0123456789abcdef0123456789abcdef";

  private final RestTemplate restTemplate;
  private final String baseUrl;

  CustomerClient(RestTemplateBuilder builder, @Value("${customer-service.base-url}") String baseUrl) {
    this.restTemplate = builder.build();
    this.baseUrl = baseUrl;
  }

  Customer getCustomer(UUID customerId) {
    return restTemplate.getForObject(baseUrl + "/customers/{id}", Customer.class, customerId);
  }

  CustomerPage listCustomers(String status, int page, int size) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Api-Key", API_KEY);
    return restTemplate.exchange(
        baseUrl + "/customers?status={status}&page={page}&size={size}",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        CustomerPage.class,
        status,
        page,
        size)
        .getBody();
  }

  /**
   * Omits {@code email} and {@code tags} when they are empty, so the request
   * matches the "only name"
   * pact; supplying them matches the "all details" pact.
   */
  Customer createCustomer(String name, String email) {
    return createCustomer(name, email, List.of());
  }

  Customer createCustomer(String name, String email, List<String> tags) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    if (email != null) {
      body.put("email", email);
    }
    if (!tags.isEmpty()) {
      body.put("tags", tags);
    }
    return restTemplate.postForObject(baseUrl + "/customers", body, Customer.class);
  }

  record Customer(UUID id, String name, String email) {
  }

  record CustomerPage(List<Customer> items, int page, int size, int total) {
  }
}
