# Boot2 / Java8 Testcontainers service connections

This module supplies the actual container lifecycle, bean registration, `@ServiceConnection`, `@ImportTestcontainers` and dynamic-property integration required by Spring AI on Boot2.
It ports21 Spring Boot3.1.12 lifecycle/context sources and shares the3.5.15 connection-factory registry port in `spring-ai-boot-service-connection-compat`.
It does not depend on the original Boot3 `spring-boot-testcontainers` jar.
Native-image/AOT support is outside this Java8 route; the ordinary JVM lifecycle and SPI are implemented and exercised with real Docker containers.

## Use with Spring AI

Install the `main-jdk8` source tree locally using JDK17 first; these snapshots have not been deployed to Maven Central.
Keep the application's Boot2.7 parent and import `cn.iocoder.spring-ai:spring-ai-bom:2.1.1-jdk8-SNAPSHOT`.
Then add the Spring AI module and whichever container implementation is needed in test scope:

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai</groupId>
    <artifactId>spring-ai-spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>cn.iocoder.spring-ai</groupId>
    <artifactId>spring-ai-opensearch-testcontainers-compat</artifactId>
    <scope>test</scope>
</dependency>
```

The first dependency supplies Spring AI connection factories plus this lifecycle bridge and the common registry.
Other container implementations remain optional; add the appropriate Java8-compatible Testcontainers1.x artifact for Chroma, Milvus, Ollama, Qdrant, Typesense, Weaviate or Docker MCP use.
The existing per-provider Spring AI dependencies must also be present; unrelated optional providers are skipped when their SDKs are absent.
For OpenSearch on a Boot2 parent, apply the HTTP5 and JSON version properties from [the client compatibility instructions](../opensearch-client/README.md).
Do not add the original Boot3 dev-services jars, Testcontainers2.x, or the original OpenSearch container jar alongside these artifacts.

```java
@Bean
@ServiceConnection
OpensearchContainer<?> opensearch() {
    return new OpensearchContainer<>("opensearchproject/opensearch:2.19.3");
}
```

Use `org.springframework.boot.testcontainers.service.connection.ServiceConnection` and `org.opensearch.testcontainers.OpensearchContainer` imports.
The Boot context starts the bean before connection details are consumed and stops it on context close.
`@ImportTestcontainers` can import static container fields and their `@DynamicPropertySource` methods.
See [the standalone installed-artifact consumer](../../compatibility-tests/boot2-testcontainers/README.md) for complete configuration and reproducible native8 validation.
This is development/test infrastructure; do not accidentally include containers in the production application's runtime dependency scope.

## Validation boundary

The source-module gate exercises the actual Boot2 lifecycle with real nginx for bean, imported-field and composed-annotation declarations, including dynamic properties, duplicate-annotation avoidance and shutdown assertions before manual cleanup.
All nine AI factory kinds have13 native8 SPI/property cases using controlled container doubles; these are separate from real Docker acceptance.
The original Spring AI OpenSearch integration test retains real MiniLM inference and its write/search/delete assertions.
The standalone Boot2 consumer uses installed jars, a real OpenSearch2.19.3 container, auto-configuration, score/filter/delete checks and automatic shutdown.
Its deterministic embedding fixture is separate from the original real-model integration test.
These checks do not represent live acceptance for every optional provider or an all200-module integration run.
The focused unit gate has58 cases, Docker integrations4 and installed-consumer tests2, all zero failures/errors/skips on native8 with retries disabled.
The200-module dependency/bytecode constraints pass; the earlier196-module8072-case full unit checkpoint remains separately recorded.
Owned API checks resolve all64 producer classes on their compile classpaths, including the declared provided Boot2 core and optional SDKs.
The active consumer's32 bridge/OpenSearch factory classes also resolve against its actual installed test/runtime classpath.
The133-jar consumer test/runtime bytecode scan has45847 effective base classes, all major52 or lower.
Whole-dependency static API findings remain separately recorded: optional Java9 Flow adapters, legacy SLF4J binder fallbacks and a reflection-filtered Unsafe member; native8 regression exercises the relevant logging and shaded Guava fallback paths.

To run the opt-in Docker tests on a real Java8 test VM:

```bash
./mvnw -pl compatibility/boot-testcontainers,spring-ai-spring-boot-testcontainers \
  -Pintegration-tests verify -Dcompat.devservices.tests=true \
  -Dit.test=Boot2ServiceConnectionRuntimeIT,OpenSearchContainerConnectionDetailsFactoryIT \
  -Dfailsafe.failIfNoSpecifiedTests=false -Djvm=/path/to/jdk8/bin/java \
  -Dcompat.opensearch.image=opensearchproject/opensearch:2.19.3
```

The original MiniLM test accepts `-Dcompat.embedding.model=file:/absolute/path/model.onnx` to avoid repeated remote downloads of a verified model.
Its default official image remains2.17.1 unless explicitly overridden for local acceptance.
Upstream file hashes and the final adapted hashes are recorded in `upstream-source-receipt.json`.
Existing upstream license headers and authors are retained.
