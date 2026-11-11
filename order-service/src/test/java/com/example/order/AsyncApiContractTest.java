package com.example.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Generates the AsyncAPI provider contract from the code (Springwolf) and checks the generated schema
 * actually constrains a message.
 *
 * <p>The negative cases are the point: a schema that accepts everything would keep PactFlow and Drift
 * green while the producer drops a field the consumer needs. If someone deletes {@code required} or
 * widens a type, one of these assertions fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncApiContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The operation the message pact references in comments.references.AsyncAPI.operationId. */
    private static final String OPERATION_ID = "order.created_send_publish";

    @LocalServerPort int port;

    @Test
    void generatedDocumentDescribesAndConstrainsTheMessage() throws Exception {
        JsonNode document = JSON.readTree(
                RestClient.create().get().uri(URI.create("http://localhost:" + port + "/springwolf/docs"))
                        .retrieve().body(String.class));

        assertTrue(document.at("/operations").has(OPERATION_ID),
                "Springwolf renamed the operation: update the AsyncAPI reference in OrderCreatedPactTest");

        JsonNode message = onlyMessage(document);
        // Springwolf has nowhere to declare a correlation ID and Drift needs one to pick the message
        // out of live traffic. Correlation is by the orderId the trigger supplied.
        ((ObjectNode) message)
                .set("correlationId", JSON.createObjectNode().put("location", "$message.payload#/orderId"));
        // Drift does not resolve a $ref for the message headers schema, so inline it.
        JsonNode headers = message.get("headers");
        if (headers != null && headers.has("$ref")) {
            ((ObjectNode) message).set("headers", resolve(document, headers));
        }

        Path output = Path.of(System.getProperty("asyncapi.output"));
        Files.createDirectories(output.getParent());
        // Indent sequences the way yamllint expects, so the committed artefact stays lint-clean.
        YAMLFactory yaml = YAMLFactory.builder()
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();
        Files.writeString(
                output,
                new ObjectMapper(yaml).writerWithDefaultPrettyPrinter().writeValueAsString(document));

        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(payloadSchema(document));

        assertTrue(schema.validate(validMessage()).isEmpty(), "the documented message must validate");

        assertRejected(schema, "missing total", withoutField("total"));
        assertRejected(schema, "missing orderId", withoutField("orderId"));
        assertRejected(schema, "total as string", withField("total", JSON.getNodeFactory().textNode("42.50")));
        assertRejected(schema, "createdAt as number", withField("createdAt", JSON.getNodeFactory().numberNode(1)));
    }

    private static void assertRejected(JsonSchema schema, String description, JsonNode message) {
        Set<ValidationMessage> errors = schema.validate(message);
        assertFalse(errors.isEmpty(), "the schema accepted a message with " + description + ": " + message);
    }

    private static JsonNode withoutField(String field) throws Exception {
        ObjectNode message = (ObjectNode) validMessage();
        message.remove(field);
        return message;
    }

    private static JsonNode withField(String field, JsonNode value) throws Exception {
        ObjectNode message = (ObjectNode) validMessage();
        message.set(field, value);
        return message;
    }

    private static JsonNode validMessage() throws Exception {
        return JSON.readTree(
                """
                {
                  "orderId": "11111111-2222-3333-4444-555555555555",
                  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "total": 42.5,
                  "createdAt": "2026-01-01T00:00:00Z"
                }
                """);
    }

    private static JsonNode onlyMessage(JsonNode document) {
        Iterator<JsonNode> messages = document.at("/components/messages").elements();
        assertTrue(messages.hasNext(), "Springwolf generated no message: is the publisher annotated?");
        JsonNode message = messages.next();
        assertFalse(messages.hasNext(), "expected exactly one message in the contract");
        return message;
    }

    private static JsonNode payloadSchema(JsonNode document) {
        JsonNode payload = onlyMessage(document).at("/payload");
        // Springwolf wraps the payload as a multi-format schema object; the schema resource is elsewhere.
        if (payload.has("schema")) {
            payload = payload.get("schema");
        }
        return resolve(document, payload);
    }

    private static JsonNode resolve(JsonNode document, JsonNode node) {
        if (!node.has("$ref")) {
            return node;
        }
        String pointer = node.get("$ref").asText().substring(2);
        JsonNode resolved = document;
        for (String part : pointer.split("/")) {
            resolved = resolved.at("/" + part);
        }
        return resolved;
    }
}
