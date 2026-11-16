# Java 8 build and runtime

This branch builds using JDK 17.0.19+ and emits Java 8 class files with `--release 8`.
Jabel 1.0.0 handles Java language syntax; it does not provide newer JDK APIs.
`Java8SourceGenerator.java` transforms source copies under `target/generated-sources/java8` and `target/generated-test-sources/java8`.
It preserves the upstream source layout and author/license headers.
Never edit generated files or use them as generator input.

Records become final classes with component accessors, canonical constructors, compact-constructor validation, Jackson 2 property annotations, and value methods.
Explicit upstream constructors/accessors/value methods remain in place.
Floating-point signed zero/NaN and array identity are verified by Java 8 tests.
Immutable collection factories keep null rejection, duplicate rules and defensive copies.
Stream.toList retains null support and an unmodifiable result.
The generator is build tooling, not a runtime dependency.

Spring 6 AOT/native-image registration classes, their nested registrars and ImportRuntimeHints annotations are omitted from the Boot 2 output.
The Boot 2 resource configuration excludes META-INF/spring/aot.factories.
This branch targets the normal Java 8 JVM; Spring 6/Boot 3 native-image infrastructure is not available on Spring 5/Boot 2.
Tool argument augmentation accepts ordinary argument DTO fields because Java 8 has no java.lang.Record.

JSpecify annotations have a separate Java 8 compatibility module; see ../jspecify/README.md.
The original annotation jar must not also appear on the Java 8 runtime classpath.

Run Maven from the checkout with its wrapper.
Set Surefire's `-Djvm=/absolute/path/to/jdk8/bin/java` to execute tests on Java 8 while Maven/compiler remain on JDK 17.
Java8CompatibilityTests deliberately asserts the actual VM version.
Compilation, runtime tests, dependency guards, full SDK support and consumer acceptance are separate gates.
The source port is in progress; this document is not a completion claim.


## Java 8 compatibility boundaries

The Java 8 line prioritizes runnable functionality with the highest verified compatible dependencies. A dependency may remain on an end-of-maintenance release; this is recorded separately from runtime compatibility. It does not imply that every upstream SDK can be used on Java 8.

Coherence vector storage is excluded from the default reactor and compatibility BOM. Its implementation uses the Coherence 24.09+ AI/HNSW API, whose SDK and test support have Java 17 bytecode. Older Java 8 Coherence releases do not supply this integration's required API. Source remains in `vector-stores/spring-ai-coherence-store` for future work; do not publish it as a Java 8-compatible artifact. Boot 3 and Boot 4 retain that integration.

Java 8 local classes cannot be deserialized as ordinary Jackson beans. A record DTO that participates in JSON deserialization must be a top-level or static member type. The source generator does not promise automatic JSON support for method-local records. Existing static records are emitted as ordinary Java 8 DTOs. Generated constructors of configuration-property DTOs use Boot 2 ConstructorBinding where needed.

The Spring Web compatibility runtime also carries the flat SSL bundle API and programmatic registry, ported from Boot 3.5.15 with original authors and Apache-2.0 headers. It does not install Boot 3 SSL property loading or certificate-file watching. Applications can register an SslBundles bean programmatically. Source artifact/file hashes are packaged in META-INF/spring-ai-compatibility/ssl-source.json. Required Spring 5/HTTP dependencies propagate from this runtime facade; it is exempt only from the pure-autoconfiguration all-optional structure rule. The separate Boot 2 framework and Java 8 bytecode guard still applies.

Boot 3 Docker Compose and Testcontainers ServiceConnection integration modules are also excluded from the Java 8 reactor/BOM. Boot 2 has no corresponding development-service engine. Ordinary Testcontainers 1.x, explicit connection properties and DynamicPropertySource remain usable; the runtime Redis Stack acceptance uses an independent disposable container. These exclusions affect development helpers, not ordinary chat/vector/MCP runtime integrations.


Generated record constructors carry Boot 2 ConstructorBinding so model-option records bind exactly as constructor-based data. The native Boot 2 core artifact is provided+optional during compilation; it is not propagated as a core-library runtime dependency. Java 8 ignores an unavailable provided annotation during reflection, which is checked by an isolated probe. The application uses its own native Boot 2 runtime.

The reactive transport stack uses Reactor BOM2024.0.18 (core3.7.19 / Reactor Netty1.2.18) and Netty BOM4.1.135.Final, whose base bytecode is Java 8. This supplies the ContextAccessor SPI required by Micrometer context-propagation1.1.3 while Spring 5 / Boot 2 remain native. The full suite and consumer gates, rather than bytecode alone, validate their behavior together.

Vendored MCP/JSpecify modules and individually identified Spring/Neo4j source ports retain upstream formatting and copyright. Project formatting/Checkstyle excludes those original source regions; handwritten compatibility runtime, framework adapters and AI changes remain subject to project checks. Runtime/bytecode/test gates still apply to every built module. Constructor/annotation changes are generated into target copies; original Java record declarations stay readable for upstream maintenance.
