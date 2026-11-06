package com.example.order;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.web.client.RestTemplateBuilder;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.Matchers;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Demonstrates query parameters, request/response header matching, and a response body with an array
 * matcher plus integer fields. Every matcher here is deliberately a <em>shape</em> matcher (regex,
 * type, min-array), never an exact value, so the provider can change values without breaking us.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "customer-service", pactVersion = PactSpecVersion.V4)
class CustomerQueryPactTest {

  private static final UUID CUSTOMER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
  private static final String REQUEST_ID = "6f1a3a52-1f2b-4c39-9c9f-2c2f0a1b7d34";

  @Pact(consumer = "order-service")
  V4Pact listCustomersPact(PactBuilder builder) {
    return builder
        .given("customers exist")
        .expectsToReceiveHttpInteraction("a request for a page of active customers",
            interaction -> {
              interaction.withRequest(request -> {
                request.method("GET");
                request.path("/customers");
                // exact values are pinned only where the provider must accept them verbatim
                request.queryParameter("status", Matchers.regexp("active|inactive", "active"));
                request.queryParameter("page", Matchers.regexp("\\d+", "2"));
                request.queryParameter("size", Matchers.regexp("\\d+", "10"));
                request.header("X-Api-Key",
                    Matchers.regexp("[a-f0-9]{32}", CustomerClient.API_KEY));
                return request;
              });
              interaction.willRespondWith(response -> {
                response.status(200);
                response.header("Content-Type",
                    Matchers.regexp("application/json(;.*)?", "application/json"));
                response.header("X-Request-Id",
                    Matchers.regexp("[0-9a-f-]{36}", REQUEST_ID));
                response.body(newJsonBody(body -> {
                  // array with at least one element; each element matched by shape
                  body.minArrayLike("items", 1, item -> {
                    item.uuid("id", CUSTOMER_ID);
                    item.stringType("name", "Alice Smith");
                    item.stringType("email", "alice@example.com");
                  });
                  body.integerType("page", 2);
                  body.integerType("size", 10);
                  body.integerType("total", 1);
                }).build());
                return response;
              });
              return interaction;
            })
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "listCustomersPact")
  void theClientListsCustomers(MockServer mockServer) {
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    CustomerClient.CustomerPage page = client.listCustomers("active", 2, 10);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).id()).isEqualTo(CUSTOMER_ID);
    assertThat(page.total()).isPositive();
  }
}
