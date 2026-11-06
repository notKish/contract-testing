package com.example.order;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.Matchers;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Demonstrates POST bodies with required vs optional fields, a 201 + {@code Location} header, a null
 * response field, and the failure case for a required field that is present but blank.
 *
 * <p>Optional fields are expressed by <em>omitting</em> them from the request body (a provider that
 * demanded them would fail), and "present vs absent" is covered by two interactions.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "customer-service", pactVersion = PactSpecVersion.V4)
class CustomerCreatePactTest {

  private static final UUID CUSTOMER_ID = UUID.fromString("8c2b1f3e-4d5a-4b6c-8d7e-9f0a1b2c3d4e");

  @Pact(consumer = "order-service")
  V4Pact createCustomerWithNameOnlyPact(PactBuilder builder) {
    return builder
        .given("a customer can be created")
        .expectsToReceiveHttpInteraction("a request to create a customer with only a name",
            interaction -> {
              interaction.withRequest(request -> {
                request.method("POST");
                request.path("/customers");
                request.header("Content-Type",
                    Matchers.regexp("application/json(;.*)?", "application/json"));
                request.body(newJsonBody(body -> {
                  // name is required; email and tags are simply absent
                  body.stringType("name", "Bob Jones");
                }).build());
                return request;
              });
              interaction.willRespondWith(response -> {
                response.status(201);
                response.header("Location",
                    Matchers.regexp("/customers/[0-9a-f-]{36}", "/customers/" + CUSTOMER_ID));
                response.body(newJsonBody(body -> {
                  body.uuid("id", CUSTOMER_ID);
                  body.stringType("name", "Bob Jones");
                  // the provider echoes a null email: assert the shape, not a value
                  body.nullValue("email");
                }).build());
                return response;
              });
              return interaction;
            })
        .toPact();
  }

  @Pact(consumer = "order-service")
  V4Pact createCustomerWithAllFieldsPact(PactBuilder builder) {
    return builder
        .given("a customer can be created")
        .expectsToReceiveHttpInteraction("a request to create a customer with all details",
            interaction -> {
              interaction.withRequest(request -> {
                request.method("POST");
                request.path("/customers");
                request.header("Content-Type",
                    Matchers.regexp("application/json(;.*)?", "application/json"));
                request.body(newJsonBody(body -> {
                  body.stringType("name", "Carol Diaz");
                  body.stringMatcher("email", "[^@]+@[^@]+\\.[a-z]{2,}", "carol@example.com");
                  // array of strings: every element matched by type (not by value)
                  body.array("tags", tags -> tags.stringType("vip"));
                }).build());
                return request;
              });
              interaction.willRespondWith(response -> {
                response.status(201);
                response.header("Location",
                    Matchers.regexp("/customers/[0-9a-f-]{36}", "/customers/" + CUSTOMER_ID));
                response.body(newJsonBody(body -> {
                  body.uuid("id", CUSTOMER_ID);
                  body.stringType("name", "Carol Diaz");
                  body.stringMatcher("email", "[^@]+@[^@]+\\.[a-z]{2,}", "carol@example.com");
                }).build());
                return response;
              });
              return interaction;
            })
        .toPact();
  }

  @Pact(consumer = "order-service")
  V4Pact createCustomerWithoutNamePact(PactBuilder builder) {
    return builder
        .given("a customer cannot be created without a name")
        .expectsToReceiveHttpInteraction("a request to create a customer with a blank name",
            interaction -> {
              interaction.withRequest(request -> {
                request.method("POST");
                request.path("/customers");
                request.header("Content-Type",
                    Matchers.regexp("application/json(;.*)?", "application/json"));
                // exact value on purpose: a blank name is the whole point of this case
                request.body(newJsonBody(body -> body.stringValue("name", "")).build());
                return request;
              });
              interaction.willRespondWith(response -> {
                response.status(400);
                response.body(newJsonBody(body -> body.stringValue("error", "name is required"))
                    .build());
                return response;
              });
              return interaction;
            })
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "createCustomerWithNameOnlyPact")
  void theClientCreatesACustomerWithOnlyAName(MockServer mockServer) {
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    CustomerClient.Customer customer = client.createCustomer("Bob Jones", null);

    assertThat(customer.id()).isNotNull();
    assertThat(customer.name()).isEqualTo("Bob Jones");
    assertThat(customer.email()).isNull();
  }

  @Test
  @PactTestFor(pactMethod = "createCustomerWithAllFieldsPact")
  void theClientCreatesACustomerWithAllDetails(MockServer mockServer) {
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    CustomerClient.Customer customer =
        client.createCustomer("Carol Diaz", "carol@example.com", java.util.List.of("vip"));

    assertThat(customer.email()).isEqualTo("carol@example.com");
  }

  @Test
  @PactTestFor(pactMethod = "createCustomerWithoutNamePact")
  void theClientSurfacesARejectedCustomer(MockServer mockServer) {
    CustomerClient client = new CustomerClient(new RestTemplateBuilder(), mockServer.getUrl());

    assertThatThrownBy(() -> client.createCustomer("", null))
        .isInstanceOf(HttpClientErrorException.BadRequest.class);
  }
}
