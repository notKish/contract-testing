# Contract testing (Pact + Gradle)

Consumer-driven contract testing between Spring Boot services, on a **Gradle** build
(the original snippets were Maven; the mapping is at the bottom).

```text
order-service (consumer)  ──HTTP──>  customer-service (provider, separate repo)
notification-service (consumer)  ──Kafka "order.created"──>  order-service (provider)
```

`customer-service` was extracted to
[notKish/customer-service](https://github.com/notKish/customer-service); it publishes its own
OpenAPI provider contract from there.

## Layout

```text
contract-testing/
├── docker-compose.broker.yml
├── build.gradle.kts                    # shared conventions (Java 21, pact.rootDir)
├── gradle/libs.versions.toml           # single place for all versions
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

The message provider test verifies **against the broker**, so start it first:

```bash
docker compose -f docker-compose.broker.yml up -d   # broker + postgres
export PACT_BROKER_BASE_URL=http://localhost:9292
export PACT_BROKER_USERNAME=pact PACT_BROKER_PASSWORD=pact

./gradlew clean build     # consumer tests -> publish -> verify from broker -> publish results
```

The task graph encodes the order: `pactPublish` depends on the consumer's `test` (that is what writes
`build/pacts`), and the message provider's `test` depends on the partner's `pactPublish`. So one
command is enough — but it needs the broker up and the credentials above, and a provider can no longer
verify a pact the consumer has not published.

`customer-service` is no longer part of this build: its pact verification and provider-contract
publishing live in the extracted repo.

**Versions are commit hashes, not a hand-edited number.** `version` is
`git rev-parse --short=12 HEAD`, set once in the root `build.gradle.kts` and used for both the
published consumer version and `pact.provider.version`. This matters: the broker keys verification
results by version, so a constant like `0.1.0` means every build overwrites the previous result and a
failing verification gets masked by the next passing one. `PACT_VERSION` overrides it (CI build
numbers, or no git checkout).

A version must identify exactly one tree, so a dirty working tree is never published:
`pactPublish` refuses to run, and test runs from a dirty tree do not publish their verification
results (`pact.verifier.publishResults` is off). Commit before publishing.

Provider branch and consumer branch default to the **checked-out git branch** (e.g. `feat/pactflow`),
not to `main`; `PACT_PROVIDER_BRANCH` and `PACT_CONSUMER_BRANCH` override them per side. Set those
in CI, where the checkout is usually a detached HEAD and the fallback is `main`.

## Bi-directional contract testing (PactFlow)

`customer-service` publishes a **provider contract** — an OpenAPI 3.0 document generated from the
application — and PactFlow compares the pacts published by `order-service` against it. Neither side
has to run the other's verification test: publishing either contract triggers the comparison.

```text
order-service pact ─┐
                    ├─► PactFlow (compares both) ─► can-i-deploy
customer-service OAS┘
```

The provider half lives in [notKish/customer-service](https://github.com/notKish/customer-service):

```bash
# in the provider repo
./gradlew publishProviderContract      # OpenAPI contract + self-verification evidence

# here
./gradlew :order-service:pactPublish   # consumer pacts
```

**PactFlow is required.** The local docker broker in `docker-compose.broker.yml` stores and verifies
pacts, but it cannot compare a pact against an OpenAPI document. Point the build at PactFlow instead
of the local broker and use a token (the build also accepts basic auth for the local broker):

```bash
export PACT_BROKER_BASE_URL=https://<tenant>.pactflow.io
export PACT_BROKER_TOKEN=<token from Settings -> API Tokens>
```

Provider-side details (springdoc document generation, the OpenAPI annotations the pacts depend on,
and the self-verification evidence PactFlow requires) are documented in that repo's README. Two
things worth knowing from here:

- PactFlow rejects `--verification-success` on its own with `selfVerificationResults.content is
  missing`; the provider task attaches a results file, so the failure is not in this repo.
- The consumer-driven verifier for these pacts now runs there (`./gradlew test`), against the same
  broker and provider version.

### Deployment safety check

Against PactFlow (the same CLI image, bearer token instead of basic auth):

```bash
alias pact-broker='docker run --rm -e PACT_BROKER_BASE_URL -e PACT_BROKER_TOKEN \
  pactfoundation/pact-cli pact-broker'

export PACT_BROKER_BASE_URL=https://<tenant>.pactflow.io
export PACT_BROKER_TOKEN=<token>

VERSION=$(./gradlew -q properties | sed -n 's/^version: //p' | head -1)   # this repo = order-service
# The provider version comes from its own repo: ./gradlew -q properties | sed -n 's/^version: //p'
PROVIDER_VERSION=<customer-service version>
pact-broker record-deployment --pacticipant customer-service --version "$PROVIDER_VERSION" --environment production
pact-broker can-i-deploy --pacticipant order-service --version "$VERSION" --to-environment production
# Computer says yes — until a provider verification or an OpenAPI comparison fails, then Computer says no.
```

Or through the Gradle plugin (order-service):

```bash
./gradlew :order-service:canIDeploy -Ppacticipant=order-service \
  -PpacticipantVersion="$VERSION" -PtoEnvironment=production
```

For the local broker, swap the alias' `-e PACT_BROKER_TOKEN` for `-e PACT_BROKER_USERNAME -e
PACT_BROKER_PASSWORD`, set `PACT_BROKER_BASE_URL=http://host.docker.internal:9292` (the CLI runs in a
container) and add `--add-host=host.docker.internal:host-gateway`.

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
- `springdoc-openapi-starter-webmvc-api` **2.8.6** generates the provider contract in the extracted
  `customer-service` repo. The document is forced to OAS 3.0 there (`springdoc.api-docs.version`),
  the broadest input for PactFlow's comparison.
- `au.com.dius.pact` Gradle plugin **4.6.21**.

## Notes and gotchas

- **Kafka needs no plugin here.** Pact models the Kafka message body/metadata as a message
  interaction; the transport (topic, broker) is your application's concern, not pact-jvm's. There is
  no `consumer:kafka` artefact on Maven Central — that is the Pact *plugin* ecosystem, only needed
  for Protobuf/gRPC/Avro payloads.
- **Broker auth is token-first.** When `PACT_BROKER_TOKEN` is set it wins over
  `PACT_BROKER_USERNAME`/`PACT_BROKER_PASSWORD` for pact publishing, `canIDeploy` and the provider
  tests. `pact-jvm` internally prefers basic auth whenever a username is present, so the token case
  blanks the username system property (`pactbroker.auth.username=""`). Getting this backwards
  yields a 401 from PactFlow, not a configuration error.
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
  so a red verification disappears as soon as a later green one is written. Using the commit hash
  makes each commit its own version, and refusing dirty publishes keeps the hash honest.
- Gradle configuration cache is on (`gradle.properties`). Anything reading `System.getenv` at
  configuration time (the broker credentials here) is configuration-cache input; pass them on the
  command line or via `~/.gradle/gradle.properties` if you hit cache invalidation noise.
