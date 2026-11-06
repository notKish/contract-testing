package com.example.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;

/**
 * Verifies the HTTP pacts published by order-service against the running
 * application.
 *
 * <p>
 * Pacts and the verification results come from and go to the Pact Broker
 * (docker-compose.broker.yml). The verifier calls the app over real HTTP, the
 * way the consumer will.
 */
@Provider("customer-service")
@PactBroker(url = "${pactbroker.url}", authentication = @PactBrokerAuth(username = "${pactbroker.auth.username}", password = "${pactbroker.auth.password}"))
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerContractVerificationTest {

  @Value("${local.server.port}")
  int port;

  @BeforeEach
  void target(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void verifyPact(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @State("customer 3fa85f64-5717-4562-b3fc-2c963f66afa6 exists")
  void customerExists() {
    // The controller serves this fixture from a constant; nothing to seed.
    // if there a db involved in setting up tests, we might need to
    // interact with db in this block
  }

  @State("customer 9f8c4a1e-0000-4000-8000-000000000000 does not exist")
  void customerDoesNotExist() {
    // Nothing to seed: CustomerController answers 404 for any id but its fixture.
  }

  @State("customers exist")
  void customersExist() {
    // The list endpoint always returns its fixture customer.
  }

  @State("a customer can be created")
  void aCustomerCanBeCreated() {
    // The controller assigns an id and echoes the submitted fields.
  }

  @State("a customer cannot be created without a name")
  void aCustomerCannotBeCreatedWithoutAName() {
    // The controller rejects a blank name with 400; nothing to seed.
  }
}
