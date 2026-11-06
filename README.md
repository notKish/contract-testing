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

The provider tests verify **against the broker**, so start it first:

```bash
docker compose -f docker-compose.broker.yml up -d   # broker + postgres
export PACT_BROKER_BASE_URL=http://localhost:9292
export PACT_BROKER_USERNAME=pact PACT_BROKER_PASSWORD=pact

./gradlew clean build     # consumer tests -> publish -> verify from broker -> publish results
```

The task graph encodes the order: `pactPublish` depends on the consumer's `test` (that is what writes
`build/pacts`), and each provider's `test` depends on the partner's `pactPublish`. So one command is
enough — but it needs the broker up and the credentials above, and a provider can no longer verify a
pact the consumer has not published.

**Versions come from git, not a hand-edited number.** `version` is
`git describe --always --dirty --abbrev=12` (e.g. `90838a7542f2`, plus `-dirty` for uncommitted
code), set once in the root `build.gradle.kts` and used for both the published consumer version and
`pact.provider.version`. This matters: the broker keys verification results by version, so a constant
like `0.1.0` means every build overwrites the previous result and a failing verification gets masked
by the next passing one. `PACT_VERSION` overrides it (CI build numbers, or no git checkout).
Provider branch is `main` (`PACT_PROVIDER_BRANCH`), consumer branch `main`
(`PACT_CONSUMER_BRANCH`).

### Deployment safety check

```bash
alias pact-broker='docker run --rm -e PACT_BROKER_BASE_URL -e PACT_BROKER_USERNAME \
  -e PACT_BROKER_PASSWORD --add-host=host.docker.internal:host-gateway pactfoundation/pact-cli pact-broker'

export PACT_BROKER_BASE_URL=http://host.docker.internal:9292   # the CLI runs in a container

VERSION=$(git describe --always --dirty --abbrev=12)
pact-broker record-deployment --pacticipant customer-service --version "$VERSION" --environment production
pact-broker can-i-deploy --pacticipant order-service --version "$VERSION" --to-environment production
# Computer says yes — until a provider verification fails, then Computer says no.
```

`/can-i-deploy` needs the *provider* version to be deployed (or tagged) somewhere, otherwise the
broker answers "no such version exists" with `deployable: null`.

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
- **Broker wiring that matters for `@PactBroker`:** `pactbroker.url` and `pactbroker.auth.*` are
  interpolated into the annotation from system properties; verification results are only published
  when `pact.verifier.publishResults=true` **and** `pact.provider.version` is set (both are set on
  the `Test` task in the provider builds). Without a provider version the broker rejects the result.
- `@PactFolder` interpolates `${...}`, but `@PactUrl` does not — that is why folder-based verification
  used a property, and why the broker path uses `@PactBroker(url = "${pactbroker.url}")`.
- Provider tests set their target in `@BeforeEach`, not inside the `@TestTemplate` body —
  `PactVerificationExtension.beforeTestExecution` needs the target already configured.
- **A constant `version` is the trap in this setup**, and it is silent: `pactPublish` and
  `pact.provider.version` both default to `project.version`, so `version = "0.1.0"` publishes and
  verifies under one broker version forever. The broker keeps the *latest* result per pact version,
  so a red verification disappears as soon as a later green one is written. Deriving the version from
  the commit (`git describe --always --dirty`) makes each commit its own version.
- Gradle configuration cache is on (`gradle.properties`). Anything reading `System.getenv` at
  configuration time (the broker credentials here) is configuration-cache input; pass them on the
  command line or via `~/.gradle/gradle.properties` if you hit cache invalidation noise.
