# Repository Guidance

Micronaut Camel integrates Apache Camel 4 with Micronaut 5. It was created from micronaut-project-template. Keep root guidance short and update it when the build changes.

## Repository Shape

- `camel-core/` is the runtime integration, published as `micronaut-camel-core` (`useStandardizedProjectNames`).
- `camel-processor/` (`micronaut-camel-processor`) generates code from the Camel catalog for classes annotated with `@CamelConfig`: the `@RequiresCamel*` annotations and the camel-main `@ConfigurationProperties` of `camel-core`, and the component, data format and language `@ConfigurationProperties` of `camel-components/`. Camel applies the options at runtime through camel-main; the generated classes document them.
- `camel-test/` (`micronaut-camel-test`) adds Camel test annotations, such as `@MockEndpoints` and `@UseAdviceWith`, to `@MicronautTest` tests.
- `camel-bom/` is the BOM. It imports `org.apache.camel:camel-bom`.
- `buildSrc/src/main/groovy/io.micronaut.build.internal.camel-*.gradle` holds the convention plugins: `camel-base`, `camel-module` for published modules, and `camel-tests` for the test suites.
- `test-suite`, `test-suite-kotlin` (KSP) and `test-suite-groovy` (Spock) hold the guide samples. Every sample is a `snippet::` with the same fully qualified name, under `io.micronaut.camel.docs`, in each suite.
- `.agents/skills/` is shared agent guidance synced from the template.

## Scope

- Routes are written in the Java DSL (including the Endpoint DSL) or the YAML DSL. XML routes and Spring XML contexts are not supported.
- Camel components are configured through Micronaut configuration (`camel.component.<name>.*`). There is no Micronaut artifact per Camel component: users add the plain `org.apache.camel` dependency.

## Versions

- Micronaut core is 5.2.x and Camel is 4.22.x (`managed-camel` in `gradle/libs.versions.toml`). The generated code follows the Camel catalog of that version, so upgrading Camel regenerates it.

## Code

- JSpecify `@NullMarked` goes only on `package-info.java`; every package has one. Use `org.jspecify.annotations.Nullable` for nullable types.

## Documentation

- User guide sources live in `src/main/docs/guide`, with navigation in `toc.yml`.
- Build the guide with `./gradlew publishGuide`.

## Verification

- `./gradlew check` runs the tests.
- Run `./gradlew publishGuide` after guide or `toc.yml` changes.

## Contributing Guidelines

- Before opening or updating a pull request, read `CONTRIBUTING.md` and follow every requirement it names.
