# Repository Guidance

Micronaut Camel integrates Apache Camel 4 with Micronaut 5. It was created from micronaut-project-template. Keep root guidance short and update it when the build changes.

## Repository Shape

- `camel-core/` is the runtime integration, published as `micronaut-camel-core` (`useStandardizedProjectNames`).
- `camel-processor/` (`micronaut-camel-processor`) is the annotation processor:
  - `CamelConfigVisitor` generates code from the Camel catalog for classes annotated with `@CamelConfig`: the `@RequiresCamel*` annotations and the camel-main `@ConfigurationProperties` of `camel-core`, and the component, data format and language `@ConfigurationProperties` of `camel-components/`. Camel applies the options at runtime through camel-main; the generated classes document them.
  - `CamelAnnotationsVisitor` handles Camel's bean annotations at compile time: `@EndpointInject`/`@Produce` become `@CamelInject`-qualified injection points served by a generated factory per class, `@Consume` methods become executable, and `@Converter` classes get a generated loader. Groovy cannot add generated sources, so there the classes are marked for Camel's runtime bean post-processor instead.
- `camel-http/` (`micronaut-camel-http`) is the `PlatformHttpEngine` of Camel's `platform-http` component (and so of the REST DSL) on the Micronaut HTTP server. A server filter dispatches the requests no Micronaut route matches to the started consumers. Its `jettyTest` test suite runs the engine on Jetty from micronaut-servlet.
- `camel-test/` (`micronaut-camel-test`) adds Camel test annotations, such as `@MockEndpoints` and `@UseAdviceWith`, to `@MicronautTest` tests.
- `camel-bom/` is the BOM. It imports `org.apache.camel:camel-bom`.
- `buildSrc/src/main/groovy/io.micronaut.build.internal.camel-*.gradle` holds the convention plugins: `camel-base`, `camel-module` for published modules, and `camel-tests` for the test suites.
- `test-suite`, `test-suite-kotlin` (KSP) and `test-suite-groovy` (Spock) hold the guide samples. Every sample is a `snippet::` with the same fully qualified name, under `io.micronaut.camel.docs`, in each suite. The suites have the Netty server, so every `@MicronautTest` starts a server.
- `test-suite/src/test/java/io/micronaut/camel/examples/<name>` ports a camel-spring-boot-examples example: its beans are `@Requires(env = "example-<name>")`, its test is `@MicronautTest(environments = "example-<name>")` and its configuration is `application-example-<name>.yml`.
- `.agents/skills/` is shared agent guidance synced from the template.

## Scope

- Routes are written in the Java DSL (including the Endpoint DSL) or the YAML DSL. XML routes and Spring XML contexts are not supported.
- Camel components are configured through Micronaut configuration (`camel.component.<name>.*`). There is no Micronaut artifact per Camel component: users add the plain `org.apache.camel` dependency. `micronaut-camel-http` is the exception, because `platform-http` needs an engine from the runtime.

## Versions

- Micronaut core is 5.2.x and Camel is 4.22.x (`managed-camel` in `gradle/libs.versions.toml`). The generated code follows the Camel catalog of that version, so upgrading Camel regenerates it.

## Code

- Integrations with other Micronaut modules live in `camel-core` sub-packages (`event`, `scheduling`, `cache`, `json`, `vault`...) behind `@Requires`, with `compileOnly` dependencies: no module per integration.

- JSpecify `@NullMarked` goes only on `package-info.java`; every package has one. Use `org.jspecify.annotations.Nullable` for nullable types.

## Documentation

- User guide sources live in `src/main/docs/guide`, with navigation in `toc.yml`.
- Build the guide with `./gradlew publishGuide`.

## Verification

- `./gradlew check` runs the tests.
- Run `./gradlew publishGuide` after guide or `toc.yml` changes.

## Contributing Guidelines

- Before opening or updating a pull request, read `CONTRIBUTING.md` and follow every requirement it names.
