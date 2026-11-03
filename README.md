# Contract testing (Pact + Gradle)

Consumer-driven contract testing between three Spring Boot services, on a **Gradle** build
(the original snippets were Maven; the mapping is at the bottom).

```text
order-service (consumer)  ──HTTP──>  customer-service (provider)
notification-service (consumer)  ──Kafka "order.created"──>  order-service (provider)
```

## Layout

```text
contract-testing/
├── docker-compose.broker.yml
├── build.gradle.kts                    # shared conventions (Java 21, pact.rootDir)
├── gradle/libs.versions.toml           # single place for all versions
├── customer-service/                   # HTTP provider
│   ├── build.gradle.kts
│   └── src/test/java/.../CustomerContractVerificationTest.java
├── order-service/                      # HTTP consumer + message provider
│   ├── src/test/java/.../CustomerPactTest.java                 # generates the HTTP pact
│   └── src/test/java/.../OrderCreatedMessageProviderTest.java  # verifies the message pact
└── notification-service/               # message consumer
    └── src/test/java/.../OrderCreatedPactTest.java             # generates the message pact
```

## Prerequisites

- JDK 21 (Temurin). Gradle 8.14 comes from the wrapper — do **not** install Maven.
- Docker (Desktop/Colima) with ≥ 8 GB RAM, only for the local Pact Broker.
  Your user must be in the `docker` group; `sudo` is not otherwise needed.
- Optional: `jq`/`curl` for broker poking. Node.js and Postman are unnecessary here.

## Run

```bash
./gradlew test             # everything, including both verifications, from a clean tree
./gradlew :order-service:test          # HTTP consumer + message provider
./gradlew :customer-service:test       # HTTP provider (runs order-service tests first)
```

Pacts are written to `<service>/build/pacts/` (`pact.rootDir`, per project). Providers read their
partner's pacts from `<partner>/build/pacts/` via `-Dpact.pactdir`, so the Gradle task graph does the
plumbing — provider `test` tasks depend on the matching consumer `test` tasks.

### Local Pact Broker

```bash
docker compose -f docker-compose.broker.yml up -d
open http://localhost:9292           # pact / pact

export PACT_BROKER_BASE_URL=http://localhost:9292
export PACT_BROKER_USERNAME=pact PACT_BROKER_PASSWORD=pact

./gradlew :order-service:pactPublish         # publishes order-service's pacts
./gradlew :order-service:pactVerify          # broker-backed verify against order-service
```

`pactPublish`/`pactVerify` come from the `au.com.dius.pact` Gradle plugin. `test` uses the JUnit 5
verification (`@PactFolder`) and needs no broker. Override the pact source with
`-Dpact.pactdir=/path/to/exported/pacts` to verify a broker export or CI artefact.

## Maven → Gradle mapping

| Maven snippet | Gradle equivalent (this repo) |
| --- | --- |
| consumer `au.com.dius.pact.consumer:junit5` (test scope) | `testImplementation(libs.pact.consumer.junit5)` — same GAV, resolved from `gradle/libs.versions.toml` |
| provider `au.com.dius.pact.provider:junit5spring` (test scope) | `testImplementation(libs.pact.provider.junit5spring)`; plain `provider:junit5` for non-Spring providers (see `order-service`, the message provider) |
| surefire `<pact.rootDir>` system property | `tasks.withType<Test> { systemProperty("pact.rootDir", ...) }` in `build.gradle.kts` — Gradle forks the test JVM too, so the property must be set on the `Test` task, not the build JVM |
| — (no Maven counterpart) | plugin `id("au.com.dius.pact")` provides `pactVerify`, `pactPublish`, `canIDeploy` |

Version pins (`gradle/libs.versions.toml`), all verified against Maven Central:

- `pact-jvm` **4.6.21** — last 4.6.x; 4.7.x exists (4.7.6) but its `junit5spring` module pins
  Spring 5.3, while 4.6.21 also works with the Spring 6 BOM below. Bump both together if you move.
- `spring-boot` **3.5.16** (Spring Framework 6.2). The `dependencyManagement` block applies Boot's
  BOM so pact-jvm's transitive Spring 5.x cannot leak into a Boot 3 app.
- `au.com.dius.pact` Gradle plugin **4.6.21**.

## Notes and gotchas

- **Kafka needs no plugin here.** Pact models the Kafka message body/metadata as a message
  interaction; the transport (topic, broker) is your application's concern, not pact-jvm's. There is
  no `consumer:kafka` artefact on Maven Central — that is the Pact *plugin* ecosystem, only needed
  for Protobuf/gRPC/Avro payloads.
- `@PactFolder("${pact.pactdir}")` is interpolated by pact-jvm; `@PactUrl` is **not**, hence the
  directory loader. The property comes from `-Dpact.pactdir=...` set on the `Test` task.
- Provider tests set their target in `@BeforeEach`, not inside the `@TestTemplate` body —
  `PactVerificationExtension.beforeTestExecution` needs the target already configured.
- Gradle configuration cache is on (`gradle.properties`). Anything reading `System.getenv` at
  configuration time (the broker credentials here) is configuration-cache input; pass them on the
  command line or via `~/.gradle/gradle.properties` if you hit cache invalidation noise.
