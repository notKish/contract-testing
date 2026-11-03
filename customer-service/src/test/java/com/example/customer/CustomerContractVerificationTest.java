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
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Verifies the HTTP pacts published by order-service against the running
 * application.
 *
 * <p>
 * Reads pacts from -Dpact.pactdir (set by this project's build script,
 * overridable for a broker export).
 * The verifier calls the app over real HTTP, the way the consumer will.
 */
@Provider("customer-service")
@PactFolder("${pact.pactdir}")
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
}
