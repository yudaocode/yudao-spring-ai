# OpenSearch Java Client 3.11 on Java 8

This artifact supplies the official OpenSearch Java Client3.11.0 payload with two original source families recompiled against `--release 8`.
The original major52 jar calls Java9 ByteBuffer.flip/position overloads; both failures were detected against an actual Java8 VM.
No SDK request/response business logic is rewritten.
Original SPDX/Apache licensing is retained, and port authors are added to the two source files.
Build uses JDK17, while tests and consumers run on native JDK8.

## Coordinates and usage

The Java8 Spring AI BOM manages `cn.iocoder.spring-ai:spring-ai-opensearch-client-compat:2.1.1-jdk8-SNAPSHOT`.
The OpenSearch vector-store starters select it automatically; ordinary SDK consumers may also depend on this artifact without adding a Spring Boot runtime.
Its provided original SDK dependency is used for compilation and does not propagate to consumers.
Do not add `org.opensearch.client:opensearch-java` alongside it, because the same Java packages would occur twice.
Java packages remain `org.opensearch.client.*`.
These coordinates require a source installation; no Maven Central deployment has taken place.

The SDK's Apache5 transport needs Reactor and HttpCore reactive support; the original build marks some transport dependencies compile-only.
This module declares them explicitly, retaining Reactor3.7.19, HTTP5.6.5/Core5.4.4, Jackson2.21.4 and the Java8-compatible JSON-P implementation.
See [the upstream build](https://github.com/opensearch-project/opensearch-java/blob/v3.11.0/java-client/build.gradle.kts).
The AWS starters additionally supply sdk-core/auth/regions/http-auth-aws, and the Java8 AWS starter supplies the SSL facade needed by its auto-configuration.

When using the native Boot2 parent, override the following inherited version properties so it does not replace the SDK's Jakarta namespace APIs with old javax namespace artifacts:

```xml
<properties>
    <httpclient5.version>5.6.5</httpclient5.version>
    <httpcore5.version>5.4.4</httpcore5.version>
    <jakarta-json.version>2.1.3</jakarta-json.version>
    <jakarta-json-bind.version>2.0.0</jakarta-json-bind.version>
    <yasson.version>2.0.2</yasson.version>
</properties>
```

The default validated mapper is Jackson2.
Original optional Jackson3/multi-release SDK paths are not part of the Java8 route.
See [the standalone Boot2 consumer](../../compatibility-tests/boot2-opensearch) for exact parent/BOM/runtime settings.

## Payload and runtime checks

`META-INF/spring-ai-compatibility/opensearch-source.json` records upstream artifact/source hashes and exact recompiled families.
`verify-payload.py ORIGINAL_JAR COMPAT_JAR` checks that every other upstream payload entry is retained byte-for-byte.
It independently derives exact class families from source paths; similarly named classes such as ApacheHttpClient5TransportBuilder must remain in the payload.
The verified payload retains6631 base classes and6610 unchanged entries outside the two recompiled source families.
Original manifests, Java9+ multi-release overlays and module descriptors are omitted for this Java8 artifact; its runtime implementation version remains3.11.0.

Native8 tests cover Unicode/reserved-character round trips, the streaming frame parser, actual synchronous/asynchronous HTTP searches and a scan of every SDK class's static JDK type/member references against the real VM.
The expanded SDK component suite passed9 cases; the earlier complete194-module clean SDK gate passed8052 cases with17 conditional skips and no failures/errors, before the added static API case.
The separate native Boot2 consumer verifies a unique installed SDK provider, correct SDK/Jackson versions, exact/approximate vector query wire data, AWS client auto-configuration, actual local SigV4 signing using a mocked HTTP transport, and an optional JSON-B mapper round trip (five cases).
It does not use a real OpenSearch engine, AWS account or paid embedding provider.
This distinguishes source compilation, unit/protocol tests, dependency checks and real-service acceptance.
