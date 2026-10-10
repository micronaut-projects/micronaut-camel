# Repository Guidance

Micronaut Camel integrates Apache Camel 4 with Micronaut 5. It was created from micronaut-project-template. Keep root guidance short and update it when the build changes.

## Repository Shape

- `camel-core/` is the runtime integration, published as `micronaut-camel-core` (`useStandardizedProjectNames`).
- `camel-processor/` (`micronaut-camel-processor`) is the annotation processor:
  - `CamelConfigVisitor` generates code from the Camel catalog for classes annotated with `@CamelConfig`: the `@RequiresCamel*` annotations of `camel-core`, and the camel-main `@ConfigurationProperties` of `camel-configuration/`, and the component, data format and language `@ConfigurationProperties` of the extension modules (`@CamelConfig(artifacts = ...)` selects the extensions of one Camel artifact). Keep generated configuration classes out of `camel-core`: Micronaut writes the configuration metadata of one annotation processing round only, and `camel-core` has configuration readers of its own (the `/camel` endpoint). Camel applies the options at runtime through camel-main; the generated classes document them.
  - `CamelAnnotationsVisitor` handles Camel's bean annotations at compile time: `@EndpointInject`/`@Produce` become `@CamelInject`-qualified injection points served by a generated factory per class, `@Consume` methods become executable, and `@Converter` classes get a generated loader. Groovy cannot add generated sources, so there the classes are marked for Camel's runtime bean post-processor instead.
- `camel-platform-http/` (`micronaut-camel-platform-http`) is the `PlatformHttpEngine` of Camel's `platform-http` component (and so of the REST DSL) on the Micronaut HTTP server. A server filter dispatches the requests no Micronaut route matches to the started consumers. Its `jettyTest` test suite runs the engine on Jetty from micronaut-servlet.
- `camel-servlet/` (`micronaut-camel-servlet`) registers Camel's `CamelHttpTransportServlet` (the `servlet` component) with the servlet containers of Micronaut Servlet, through a `ServletContainerInitializer` bean, configured with `camel.servlet.mapping.*` (read with `@Value`, not `@ConfigurationProperties`: the module also holds the generated `servlet` component configuration, and Micronaut writes the configuration metadata of one processing round only). Its tests run on Jetty (`test`), Tomcat (`tomcatTest`) and Undertow (`undertowTest`), from the same sources; the initializer destroys the servlet itself on shutdown, as Undertow does not and Camel's servlet registry is JVM-wide.
- `camel-core/` also holds the operations integrations, in `io.micronaut.camel.health`, `.management`, `.micrometer` and `.tracing`. They use `compileOnly` dependencies on micronaut-management, micronaut-micrometer, micronaut-tracing and the Camel artifacts, and are gated with `@Requires`, so they are only active when the application adds those modules.
- `camel-core` also integrates Camel's messaging components with the Micronaut messaging modules, in the `io.micronaut.camel.kafka`, `.jms`, `.rabbitmq`, `.nats` and `.mqtt` packages: `ComponentCustomizer` and factory beans that configure a component from the module's configuration when both are on the classpath (`compileOnly` dependencies, `@Requires(classes = ...)` plus the generated `@RequiresCamel*` annotation), never overriding `camel.component.<name>.*`. Their own settings are under `micronaut.camel.<integration>.*`.
- `camel-test/` (`micronaut-camel-test`) adds Camel test annotations, such as `@MockEndpoints` and `@UseAdviceWith`, to `@MicronautTest` tests.
- `camel-bom/` is the BOM. It imports `org.apache.camel:camel-bom`.
- `buildSrc/src/main/groovy/io.micronaut.build.internal.camel-*.gradle` holds the convention plugins (and `buildSrc/src/main/groovy/io/micronaut/build/camel/` their task classes, un-ignored in `.gitignore`): `camel-base`, `camel-module` for published modules, and `camel-tests` for the test suites.
- `test-suite`, `test-suite-kotlin` (KSP) and `test-suite-groovy` (Spock) hold the guide samples. Every sample is a `snippet::` with the same fully qualified name, under `io.micronaut.camel.docs`, in each suite. The suites have the Netty server, so every `@MicronautTest` starts a server.
- `test-suite-python` holds Python (Pyronaut) versions of some samples, under `micronaut.camel.docs` (the macro drops the leading `io.`). It runs with `./gradlew :test-suite-python:test -Ppython-ci` on GraalVM. A snippet without a Python version lists its languages: `languages="java,kotlin,groovy"`. Python classes do not keep Micronaut annotations on the generated Java class, so read annotations from the bean definition's annotation metadata, not by reflection.
- `test-suite/src/test/java/io/micronaut/camel/examples/<name>` ports a camel-spring-boot-examples example: its beans are `@Requires(env = "example-<name>")`, its test is `@MicronautTest(environments = "example-<name>")` and its configuration is `application-example-<name>.yml`.
- `test-suite-graal` runs Camel in a native image with `./gradlew :test-suite-graal:nativeTest` (GraalVM 25). The `CamelNativeFeature` of `camel-core` registers the classes of Camel's service files and the classes the processor lists under `META-INF/micronaut-camel/native`; keep new reflective or service-file-based features covered there.
- `camel-core` has the `cli` package: a `CliConnectorFactory` bean for `camel-cli-connector` (Camel JBang's CLI) whose connector closes the Micronaut application context on `camel stop`; `camel.cli.enabled=false` disables the connector, which Camel otherwise enables from the classpath. Cluster services (`camel.cluster.*` or `CamelClusterService` beans), the supervising route controller (`camel.routecontroller.*`) and the debugger (`camel.debug.*`) need no code: camel-main applies them, and `camel-core` tests cover them.
- `camel-core` bridges `hazelcast` (the `HazelcastInstance` of micronaut-cache-hazelcast), `ehcache` (the Ehcache `CacheManager` and `ehcache.caches.*` of micronaut-cache-ehcache) and `gcp` (the project of micronaut-gcp: the `projectId` component option of the Google components, and the `{{gcp.project-id}}` placeholder through a `PropertiesSource` bean); Pulsar's `pulsarClient` is autowired by Camel. Their tests, and those of the CLI connector, are in the `ecosystemTest` and `cliTest` test suites of `camel-core`, which keep those dependencies off the main test classpath (`cliTest` runs with its own `user.home`, where the connector writes its files).
- `camel-core` integrates other Micronaut modules in sub-packages, behind `@Requires(classes = ...)` with `compileOnly` dependencies: `transaction` runs `transacted()` routes in Micronaut Data transactions (`MicronautTransactionPolicy`, named `PROPAGATION_*` policy beans, a transactional error handler), and `jdbc` gives Camel a `DataSource` that joins the current Micronaut transaction and works outside one. Registry beans are adapted for Camel by `CamelBeanAdapter` beans.
- The test suites' `application.yml` configures an H2 data source with `transaction-manager: springJdbc` (micronaut-data-spring-jdbc) for the transaction samples. Examples needing PostgreSQL or Kafka start them with Testcontainers and are skipped without a container runtime.
- `test-suite` has Micronaut Security on the classpath but disabled (`micronaut.security.enabled: false` in `application.yml`); the tests that need it use their own environment. Likewise micronaut-cache-infinispan is disabled (`infinispan.enabled: false`) except in the infinispan example. Every guide sample route loads in every test of its suite, including the example tests: the REST DSL samples bind JSON, so example REST services that do not bind set `bindingMode: off`.
- Tests that need a service (Redis, LocalStack, Azurite, Vault, PostgreSQL, Qdrant, Infinispan, ArangoDB, HAPI FHIR) start it with Testcontainers in a `TestPropertyProvider` and stop it in `@AfterAll`. HTTP services that are public APIs or too heavy for a container (docling-serve) are `@Controller`s of the example's environment, with the server on a `SocketUtils` port that the `TestPropertyProvider` sets. Under Podman, run them with `TESTCONTAINERS_RYUK_DISABLED=true`; containers whose Testcontainers module mounts the Docker socket (LocalStack) run as generic containers.
- `.agents/skills/` is shared agent guidance synced from the template.

## Scope

- Routes are written in the Java DSL (including the Endpoint DSL) or the YAML DSL. XML routes and Spring XML contexts are not supported.
- Camel components are configured through Micronaut configuration (`camel.component.<name>.*`).
- Every Camel artifact with components, data formats or languages has a module, `micronaut-camel-<x>` for `camel-<x>`, with no directory in the repository. The `io.micronaut.build.internal.camel-extensions` settings plugin (`settings-build-logic/`) includes one project per artifact of the Camel catalog, with its directory under `target/camel-extensions/`, and applies the `io.micronaut.build.internal.camel-extension` convention plugin of `buildSrc` to it. That plugin writes a `@CamelConfig(artifacts = "camel-<x>")` class into the generated sources, in `io.micronaut.camel.components.<x>`, and adds the `api` dependencies on `micronaut-camel-core` and the Camel artifact. A real project named after a Camel artifact, such as `camel-platform-http`, is that artifact's module and gets the same configuration. The `generateCamelExtensionsReference` task of the root project writes the module table of the guide. Exclusions (extensions needing Spring Boot) are listed in the settings plugin.
- All modules, the extension modules included, are released to Maven Central in the single bundle of micronaut-build's `prepareBundle`/`publishToMavenCentral`. The extension modules build their javadoc with `--no-fonts`, which keeps the bundle small.

## Versions

- Micronaut core is 5.2.x, micronaut-micrometer 6.1.x and micronaut-tracing 8.3.x (catalogs `mnMicrometer` and `mnTracing`); the other module catalogs (`mnSecurity`, `mnRedis`, `mnValidation`, `mnObjectStorage`, `mnMcp`, `mnLangchain4j`, `mnAws`, `mnAzure`, `mnSql`, `mnGcp`, `mnPulsar`) follow the versions of Micronaut Platform 5.2, and Camel is 4.22.x (`managed-camel` in `gradle/libs.versions.toml`). The generated code follows the Camel catalog of that version, so upgrading Camel regenerates it.

## Code

- Integrations with other Micronaut modules live in `camel-core` sub-packages (`event`, `scheduling`, `cache`, `json`, `vault`, `security`, `redis`, `objectstorage`, `httpclient`, `mcp`, `cli`, `hazelcast`, `ehcache`, `gcp`...) behind `@Requires`, with `compileOnly` dependencies: no module per integration. Components of `camel-core` (`micronaut-event`, `object-storage`, `micronaut-http`) are `@Named` beans that the registry bridge finds by scheme. The MCP integration plugs into camel-main's `camel.server.mcp-*` options with a `McpServerFactory` bean, so it needs only `camel-mcp-server-api`.

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
