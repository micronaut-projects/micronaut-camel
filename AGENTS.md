# Repository Guidance

Micronaut Camel integrates Apache Camel 4 with Micronaut 5. It was created from micronaut-project-template. Keep root guidance short and update it when the build changes.

## Repository Shape

- `camel-core/` is the runtime integration, published as `micronaut-camel-core` (`useStandardizedProjectNames`).
- `camel-processor/` (`micronaut-camel-processor`) is the annotation processor:
  - `CamelConfigVisitor` generates code from the Camel catalog for classes annotated with `@CamelConfig`: the `@RequiresCamel*` annotations of `camel-core`, and the camel-main, component, data format and language `@ConfigurationProperties` of `camel-components/`. Keep generated configuration classes out of `camel-core`: Micronaut writes the configuration metadata of one annotation processing round only, and `camel-core` has configuration readers of its own (the `/camel` endpoint). Camel applies the options at runtime through camel-main; the generated classes document them.
  - `CamelAnnotationsVisitor` handles Camel's bean annotations at compile time: `@EndpointInject`/`@Produce` become `@CamelInject`-qualified injection points served by a generated factory per class, `@Consume` methods become executable, and `@Converter` classes get a generated loader. Groovy cannot add generated sources, so there the classes are marked for Camel's runtime bean post-processor instead.
- `camel-http/` (`micronaut-camel-http`) is the `PlatformHttpEngine` of Camel's `platform-http` component (and so of the REST DSL) on the Micronaut HTTP server. A server filter dispatches the requests no Micronaut route matches to the started consumers. Its `jettyTest` test suite runs the engine on Jetty from micronaut-servlet.
- `camel-core/` also holds the operations integrations, in `io.micronaut.camel.health`, `.management`, `.micrometer` and `.tracing`. They use `compileOnly` dependencies on micronaut-management, micronaut-micrometer, micronaut-tracing and the Camel artifacts, and are gated with `@Requires`, so they are only active when the application adds those modules.
- `camel-core` also integrates Camel's messaging components with the Micronaut messaging modules, in the `io.micronaut.camel.kafka`, `.jms`, `.rabbitmq`, `.nats` and `.mqtt` packages: `ComponentCustomizer` and factory beans that configure a component from the module's configuration when both are on the classpath (`compileOnly` dependencies, `@Requires(classes = ...)` plus the generated `@RequiresCamel*` annotation), never overriding `camel.component.<name>.*`. Their own settings are under `micronaut.camel.<integration>.*`.
- `camel-test/` (`micronaut-camel-test`) adds Camel test annotations, such as `@MockEndpoints` and `@UseAdviceWith`, to `@MicronautTest` tests.
- `camel-bom/` is the BOM. It imports `org.apache.camel:camel-bom`.
- `buildSrc/src/main/groovy/io.micronaut.build.internal.camel-*.gradle` holds the convention plugins: `camel-base`, `camel-module` for published modules, and `camel-tests` for the test suites.
- `test-suite`, `test-suite-kotlin` (KSP) and `test-suite-groovy` (Spock) hold the guide samples. Every sample is a `snippet::` with the same fully qualified name, under `io.micronaut.camel.docs`, in each suite. The suites have the Netty server, so every `@MicronautTest` starts a server.
- `test-suite/src/test/java/io/micronaut/camel/examples/<name>` ports a camel-spring-boot-examples example: its beans are `@Requires(env = "example-<name>")`, its test is `@MicronautTest(environments = "example-<name>")` and its configuration is `application-example-<name>.yml`.
- `test-suite-graal` runs Camel in a native image with `./gradlew :test-suite-graal:nativeTest` (GraalVM 25). The `CamelNativeFeature` of `camel-core` registers the classes of Camel's service files and the classes the processor lists under `META-INF/micronaut-camel/native`; keep new reflective or service-file-based features covered there.
- `camel-core` integrates other Micronaut modules in sub-packages, behind `@Requires(classes = ...)` with `compileOnly` dependencies: `transaction` runs `transacted()` routes in Micronaut Data transactions (`MicronautTransactionPolicy`, named `PROPAGATION_*` policy beans, a transactional error handler), and `jdbc` gives Camel a `DataSource` that joins the current Micronaut transaction and works outside one. Registry beans are adapted for Camel by `CamelBeanAdapter` beans.
- The test suites' `application.yml` configures an H2 data source with `transaction-manager: springJdbc` (micronaut-data-spring-jdbc) for the transaction samples. Examples needing PostgreSQL or Kafka start them with Testcontainers and are skipped without a container runtime.
- `test-suite` has Micronaut Security on the classpath but disabled (`micronaut.security.enabled: false` in `application.yml`); the tests that need it use their own environment. Every guide sample route loads in every test of its suite, including the example tests: the REST DSL samples bind JSON, so example REST services that do not bind set `bindingMode: off`.
- Tests that need a service (Redis, LocalStack, Azurite, Vault, PostgreSQL, Qdrant) start it with Testcontainers in a `TestPropertyProvider` and stop it in `@AfterAll`. Under Podman, run them with `TESTCONTAINERS_RYUK_DISABLED=true`; containers whose Testcontainers module mounts the Docker socket (LocalStack) run as generic containers.
- `.agents/skills/` is shared agent guidance synced from the template.

## Scope

- Routes are written in the Java DSL (including the Endpoint DSL) or the YAML DSL. XML routes and Spring XML contexts are not supported.
- Camel components are configured through Micronaut configuration (`camel.component.<name>.*`). There is no Micronaut artifact per Camel component: users add the plain `org.apache.camel` dependency. `micronaut-camel-http` is the exception, because `platform-http` needs an engine from the runtime.

## Versions

- Micronaut core is 5.2.x, micronaut-micrometer 6.1.x and micronaut-tracing 8.3.x (catalogs `mnMicrometer` and `mnTracing`); the other module catalogs (`mnSecurity`, `mnRedis`, `mnValidation`, `mnObjectStorage`, `mnMcp`, `mnLangchain4j`, `mnAws`, `mnAzure`, `mnSql`) follow the versions of Micronaut Platform 5.2, and Camel is 4.22.x (`managed-camel` in `gradle/libs.versions.toml`). The generated code follows the Camel catalog of that version, so upgrading Camel regenerates it.

## Code

- Integrations with other Micronaut modules live in `camel-core` sub-packages (`event`, `scheduling`, `cache`, `json`, `vault`, `security`, `redis`, `objectstorage`, `httpclient`, `mcp`...) behind `@Requires`, with `compileOnly` dependencies: no module per integration. Components of `camel-core` (`micronaut-event`, `object-storage`, `micronaut-http`) are `@Named` beans that the registry bridge finds by scheme. The MCP integration plugs into camel-main's `camel.server.mcp-*` options with a `McpServerFactory` bean, so it needs only `camel-mcp-server-api`.

- JSpecify `@NullMarked` goes only on `package-info.java`; every package has one. Use `org.jspecify.annotations.Nullable` for nullable types.

## Documentation

- User guide sources live in `src/main/docs/guide`, with navigation in `toc.yml`.
- Build the guide with `./gradlew publishGuide`.

## Verification

- `./gradlew check` runs the tests. The messaging tests start brokers with Testcontainers (in `TestPropertyProvider` tests); with Podman, Ryuk may fail to mount the socket, so run them with `TESTCONTAINERS_RYUK_DISABLED=true`. The tests stop their containers themselves.
- `./gradlew check` runs the tests. With Podman, run it with `TESTCONTAINERS_RYUK_DISABLED=true`, as Ryuk cannot mount the Podman socket.
- Run `./gradlew publishGuide` after guide or `toc.yml` changes.

## Contributing Guidelines

- Before opening or updating a pull request, read `CONTRIBUTING.md` and follow every requirement it names.
