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
├── docker-compose.broker.yml           # Pact Broker + Postgres
├── docker-compose.kafka.yml            # Kafka (KRaft) + the order.created topic
├── docs/asyncapi/order-service-messages.yaml   # generated, committed provider contract
├── drift/order-created.testcases.yaml          # checks the running producer
├── build.gradle.kts                    # shared conventions (Java 21, pact.rootDir)
├── gradle/libs.versions.toml           # single place for all versions
├── order-service/                      # HTTP consumer + Kafka producer
│   ├── src/main/java/.../OrderController.java           # POST /orders: the Drift trigger
│   ├── src/main/java/.../OrderCreatedEventPublisher.java # the real Kafka producer
│   └── src/test/java/.../CustomerPactTest.java          # generates the HTTP pacts
└── notification-service/               # message consumer
    └── src/test/java/.../OrderCreatedPactTest.java      # generates the message pact
```

## Prerequisites

- JDK 21 (Temurin). Gradle 8.14 comes from the wrapper — do **not** install Maven.
- Docker (Desktop/Colima) with ≥ 8 GB RAM, for the local Pact Broker and, when you run the Drift
  check, Kafka. Your user must be in the `docker` group; `sudo` is not otherwise needed.
- Optional: `jq`/`curl` for broker poking. Node.js only for the CI
  `asyncapi diff` gate (`npx @asyncapi/cli`); nothing here needs Postman.

## Run

Publishing and the message contract need the broker:

```bash
docker compose -f docker-compose.broker.yml up -d   # broker + postgres
export PACT_BROKER_BASE_URL=http://localhost:9292
export PACT_BROKER_USERNAME=pact PACT_BROKER_PASSWORD=pact

./gradlew clean build     # consumer tests -> publish pacts
```

`pactPublish` depends on the consumer's tests, which is what writes `build/pacts`. Nothing verifies a
pact at test time any more: PactFlow compares the message pact against the provider's AsyncAPI
document, and Drift checks the running producer against that same document (see below).

`customer-service` is no longer part of this build: its pact verification and provider-contract
publishing live in the extracted repo.

**Versions are commit hashes, not a hand-edited number.** `version` is
`git rev-parse --short=12 HEAD`, set once in the root `build.gradle.kts` and used for the published
consumer version and, in CI, for the published provider contract. This matters: the broker keys
results by version, so a constant like `0.1.0` means every build overwrites the previous result and a
failing comparison gets masked by the next passing one. `PACT_VERSION` overrides it (CI build
numbers, or no git checkout).

A version must identify exactly one tree, so a dirty working tree is never published: `pactPublish`
refuses to run. The AsyncAPI provider contract is published from the CI checkout, which is clean by
construction.

The consumer branch recorded with each published pact defaults to the **checked-out git branch**
(e.g. `feat/pactflow`), not to `main`; `PACT_CONSUMER_BRANCH` overrides it. The AsyncAPI provider
contract is published by CI with `PACT_PROVIDER_BRANCH`, which is passed straight to the CLI.

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
- The provider is BDCT-only: it publishes the OAS with self-verification evidence and no longer
  runs the pact verifier, so there are no consumer-driven results for this pair. Keeping the
  OpenAPI document honest (attach a conformance run as evidence) is what makes the comparison
  meaningful.

### Kafka message contracts (AsyncAPI + Drift)

The `order.created` event is a BDCT pair like the HTTP one, but the provider contract is an
**AsyncAPI 3.0** document, not OpenAPI:

```text
notification-service pact ─┐
                           ├─► PactFlow (compares both) ─► can-i-deploy
order-service AsyncAPI    ─┘
```

`docs/asyncapi/order-service-messages.yaml` is **generated from the producer** by
[Springwolf](https://www.springwolf.dev/). `@AsyncPublisher` on `OrderCreatedEventPublisher.publish`
supplies the channel, operation, description and headers; the payload schema comes from
`OrderCreatedEvent` (`format: uuid`/`date-time`, and `required` from
`@Schema(requiredMode = REQUIRED)`). Springwolf derives the operation id
(`order.created_send_publish`), and the pact names it with `.reference("AsyncAPI", "operationId",
...)`; its `contentType` metadata is compared against the headers schema.

Publish it (`--specification` defaults to `oas`):

```bash
docker run --rm -v "$PWD:$PWD" -w "$PWD" \
  -e PACT_BROKER_BASE_URL -e PACT_BROKER_TOKEN \
  pactfoundation/pact-cli pactflow publish-provider-contract \
  docs/asyncapi/order-service-messages.yaml \
  --provider order-service-messages \
  --provider-app-version "$(git rev-parse --short=12 HEAD)" \
  --branch "$(git rev-parse --abbrev-ref HEAD)" \
  --specification asyncapi \
  --content-type application/yaml \
  --verification-exit-code 0 \
  --verification-results drift/output/results/verification.<timestamp>.result \
  --verification-results-content-type application/vnd.smartbear.drift.result \
  --verifier drift
```

That evidence comes from **Drift**, the only tool here that touches a real broker: it subscribes to
`order.created`, runs the trigger in `drift/order-created.testcases.yaml` (a `POST /orders`),
captures the message by its `correlation-id` header and validates payload and headers against the
document.

```bash
docker compose -f docker-compose.kafka.yml up -d
./gradlew :order-service:bootRun &

mkdir -p ~/.local/drift
curl -sL https://download.pactflow.io/drift/latest/linux-x86_64.tgz \
  | tar xz -C ~/.local/drift
# The tarball bundles the core plugins only; Kafka is an optional component.
~/.local/drift/drift component install kafka --yes
~/.local/drift/drift verify -f drift/order-created.testcases.yaml \
  --output-dir drift/output --generate-result
```

What this buys you, and what it does not:

- BDCT proves the consumer's sample fits the document and its metadata fits the headers schema.
- Drift proves the **running producer** emits a message that fits the document, over a real broker
  (serializer, headers, topic included).
- Neither proves the document is *strict or complete* on its own. That is split three ways:
  `required` comes from `@Schema(requiredMode = REQUIRED)` on the payload, `AsyncApiContractTest`
  asserts the schema **rejects** the messages that matter, and the `asyncapi diff` CI gate fails on
  removed or renamed nodes. `additionalProperties` is intentionally left open, so additive changes
  are not breaking.
- Drift is example-driven: one test case exercises one code path, not every schema branch. It also
  needs its Kafka transport plugin installed separately (`drift component install kafka --yes`); the
  release tarball ships the core plugins only.
- Topics must exist first (`KAFKA_AUTO_CREATE_TOPICS_ENABLE` is off); Drift does not create them.

Generate the document, and re-generate it after any change to the producer or payload:

```bash
./gradlew :order-service:updateAsyncApiDoc   # writes docs/asyncapi/order-service-messages.yaml
```

`AsyncApiContractTest` does the generating and the guarding in one place: it boots the app, reads
`/springwolf/docs`, adds the two things the generator cannot express (the `correlationId` location
Drift needs, and the inlined headers schema — Drift does not resolve a `$ref` there), writes the
document, then validates sample messages against the generated schema. The
negative cases are the point — a message without `total` or `orderId`, a string `total` and a numeric
`createdAt` must all be rejected. Delete `required` or widen a type and the build goes red; without
that, BDCT and Drift would happily certify a schema that accepts anything. CI fails if the committed
copy differs from the generated one, and the `asyncapi diff` gate against the base branch fails on
removed or renamed nodes.

Springwolf is pinned to **1.21.0**: it is the last line built for Spring Boot 3.5. 2.0.0+ targets
Boot 4 and fails to start here with `Could not find class
[org.springframework.boot.thread.Threading]`.

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
| provider `au.com.dius.pact.provider:junit5spring` / `provider:junit5` (test scope) | not used here: the provider side publishes a contract instead of running a verifier. The catalog still lists both modules. |
| surefire `<pact.rootDir>` system property | `tasks.withType<Test> { systemProperty("pact.rootDir", ...) }` in `build.gradle.kts` — Gradle forks the test JVM too, so the property must be set on the `Test` task, not the build JVM |
| — (no Maven counterpart) | plugin `id("au.com.dius.pact")` provides `pactVerify`, `pactPublish`, `canIDeploy` |

Version pins (`gradle/libs.versions.toml`), all verified against Maven Central:

- `pact-jvm` **4.7.7** — 4.7.1 adds `reference()` to the V4 message interaction builder, which is
  how a message pact names its AsyncAPI operation; 4.6.21 cannot emit it. The 4.7.x `junit5spring`
  module pins Spring 5.3, but it is not on any classpath here.
- `spring-boot` **3.5.16** (Spring Framework 6.2). The `dependencyManagement` block applies Boot's
  BOM so pact-jvm's transitive Spring 5.x cannot leak into a Boot 3 app.
- `springdoc-openapi-starter-webmvc-api` **2.8.6** generates the provider contract in the extracted
  `customer-service` repo. The document is forced to OAS 3.0 there (`springdoc.api-docs.version`),
  the broadest input for PactFlow's comparison.
- `au.com.dius.pact` Gradle plugin **4.7.7**.
- `springwolf-kafka` **1.21.0** generates the AsyncAPI document from the publisher annotations. 1.x
  is the last line built for Spring Boot 3.5; 2.0.0+ needs Boot 4. `swagger-annotations-jakarta`
  **2.2.40** supplies `@Schema(requiredMode = REQUIRED)` — Springwolf only pulls `swagger-models`.
  `json-schema-validator` **1.5.9** checks sample messages against the generated payload schema in
  `AsyncApiContractTest`.

## Notes and gotchas
- **open source bi-directional contract testing**: https://github.com/pactflow/openapi-pact-comparator. 
  lets you compare a consumer pact with an openapi specification. This has been inspired by swagger-
  mock-validator. however, we will not get the full features of pactflow bdct like no broker (doesn't store
  contracts), no `can-i-deploy`, environments or record-deployment. no fetching of pacts. cheap setup would 
  be self-host pact broker and have consumer publish pacts with --branch. In provider CI, fetch the pacts for 
  main branch and deployed consumers, then run them through Comparator against your OAS. run oas(open api spec)
  through a verifier like (schemathesis), so that oas is trusted. Block any build on failure.
- **Kafka needs no plugin here.** Pact models the Kafka message body/metadata as a message
  interaction; the transport (topic, broker) is your application's concern, not pact-jvm's. There is
  no `consumer:kafka` artefact on Maven Central — that is the Pact *plugin* ecosystem, only needed
  for Protobuf/gRPC/Avro payloads. The topic is never checked by pact-jvm: it is checked by the
  AsyncAPI headers schema (via `metadata`) and by Drift.
- **Broker auth is token-first.** When `PACT_BROKER_TOKEN` is set it wins over
  `PACT_BROKER_USERNAME`/`PACT_BROKER_PASSWORD` for pact publishing, `canIDeploy` and the provider
  contract. `pact-jvm` internally prefers basic auth whenever a username is present, so the token
  case blanks the username system property (`pactbroker.auth.username=""`). Getting this backwards
  yields a 401 from PactFlow, not a configuration error.
- **A message pact must name its AsyncAPI operation.** `.reference("AsyncAPI", "operationId", ...)`
  is written to `comments.references.AsyncAPI.operationId`; without it the comparison fails with
  `message.references.missing`. `reference()` only exists from pact-jvm 4.7.1, on
  `MessageInteractionBuilder` via `PactBuilder` — the older `MessagePactBuilder` cannot emit it.
- **What BDCT compares for a message:** the pact body's sample values against the message `payload`
  schema, and the pact `metadata` against the message `headers` schema. Matching rules
  (`decimalType`, `uuid`, ...) are ignored, so the sample values must satisfy the JSON Schema. The
  channel address is never compared with `metadata.kafka_topic` — that only works because
  `kafka_topic` is declared in the headers schema with a `const`.
- **A constant `version` is the trap in this setup**, and it is silent: `pactPublish` defaults to
  `project.version`, so `version = "0.1.0"` publishes under one broker version forever. The broker
  keeps the *latest* result per contract version, so a red comparison disappears as soon as a later
  green one is written. Using the commit hash makes each commit its own version, and refusing dirty
  publishes keeps the hash honest.
- Gradle configuration cache is on (`gradle.properties`). Anything reading `System.getenv` at
  configuration time (the broker credentials here) is configuration-cache input; pass them on the
  command line or via `~/.gradle/gradle.properties` if you hit cache invalidation noise.
- 
