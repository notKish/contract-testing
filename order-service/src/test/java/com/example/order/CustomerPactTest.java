package com.example.order;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Consumer side of order-service -> customer-service. Writes
 * build/pacts/order-service-customer-service.json, which customer-service
 * verifies.
 *
 * <p>One @Pact method per interaction: the mock server expects every
 * interaction in the pact it is started with to be replaved, so interactions
 * that belong to different tests live in different pacts (pact-jvm merges them
 * into the one consumer-provider file on write).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "customer-service", pactVersion = PactSpecVersion.V4)
class CustomerPactTest {

  private static final UUID CUSTOMER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

  /** A customer the provider must not know about. */
  private static final UUID MISSING_CUSTOMER_ID =
      UUID.fromString("9f8c4a1e-0000-4000-8000-000000000000");

  @Pact(consumer = "order-service")
  V4Pact getExistingCustomerPact(PactBuilder builder) {
    return builder
        .given("customer 3fa85f64-5717-4562-b3fc-2c963f66afa6 exists")
        .expectsToReceiveHttpInteraction("a request for an existing customer",
            interaction -> interaction
                .withRequest(request -> request
                    .method("GET")
                    .path("/customers/" + CUSTOMER_ID))
                .willRespondWith(response -> response
                    .status(200)
                    .body(newJsonBody(body -> {
                      body.uuid("id", CUSTOMER_ID);
                      body.stringType("name", "Alice Smith");
                      body.stringType("email", "alice@example.com");
                    }).build())))
        .toPact();
  }

  @Pact(consumer = "order-service")
  V4Pact getMissingCustomerPact(PactBuilder builder) {
    return builder
        .given("customer 9f8c4a1e-0000-4000-8000-000000000000 does not exist")
        .expectsToReceiveHttpInteraction("a request for a customer that does not exist",
            interaction -> interaction
                .withRequest(request -> request
                    .method("GET")
                    .path("/customers/" + MISSING_CUSTOMER_ID))
                .willRespondWith(response -> response
                    .status(404)))
        .toPact();
  }

  
  @Test
  @PactTestFor(pactMethod = "getExistingCustomerPact")
  void theCustomerClientReadsTheCustomer(MockServer mockServer) {
    // The real client is exercised against the mock server, so a change in the
    // response shape fails here.
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    CustomerClient.Customer customer = client.getCustomer(CUSTOMER_ID);

    org.assertj.core.api.Assertions.assertThat(customer.id()).isEqualTo(CUSTOMER_ID);
    org.assertj.core.api.Assertions.assertThat(customer.name()).isNotBlank();
  }

  @Test
  @PactTestFor(pactMethod = "getMissingCustomerPact")
  void theCustomerClientFailsWhenTheCustomerDoesNotExist(MockServer mockServer) {
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    org.assertj.core.api.Assertions
        .assertThatThrownBy(() -> client.getCustomer(MISSING_CUSTOMER_ID))
        .isInstanceOf(HttpClientErrorException.NotFound.class);
  }
}
