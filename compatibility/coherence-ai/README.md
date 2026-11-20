# Coherence vector capabilities on Java 8

This module ports the Coherence24.09 vector and HNSW source families onto the Java8-capable Coherence14.1.1-0-29 core.
It does not run the original Java17 Coherence24.09 runtime on Java8.
The public Spring AI `Session` builder and native-client contract remain available.

## Use

Import `cn.iocoder.spring-ai:spring-ai-bom:2.1.1-jdk8-SNAPSHOT`, then depend on `cn.iocoder.spring-ai:spring-ai-coherence-store`.
The store transitively supplies `spring-ai-coherence-ai-compat` and the pinned Java8 core.
The coordinates are source snapshots; install them locally before use, as they are not yet published to Maven Central.
Do not add the original `coherence-hnsw:24.09` or a Coherence24.09 core alongside these jars: they would introduce duplicate classes or Java17 bytecode.

For POF caches, every cluster member must explicitly load the supplied configuration:

```properties
coherence.serializer=pof
coherence.pof.config=coherence-ai-java8-pof-config.xml
```

Core14 does not automatically discover the newer `PofConfigProvider` service.
The supplied configuration includes the core14 POF types, upstream vector IDs925/926/927/928/929/930/932/935/951/952, HNSW959, and a compatibility registration958 for `BinaryQuantIndex`.
If you maintain a custom POF configuration, include this file and reserve these IDs consistently on every member.
Do not include it twice through separate configuration branches.

```java
Session session = Session.create();
CoherenceVectorStore store = CoherenceVectorStore.builder(session, embeddingModel)
    .mapName("documents")
    .indexType(CoherenceVectorStore.IndexType.HNSW)
    .build();
store.afterPropertiesSet();
```

Applications own the `Session` lifecycle and close it on shutdown.
Core14's default session is a storage-disabled client and requires an existing storage member.
For a standalone local application or test, set `-Dcoherence.distributed.localstorage=true` before creating the session; production clients normally connect to separately configured storage members.
The Boot2 consumer fixture demonstrates a real application context and installed jars at `compatibility-tests/boot2-coherence`.
No Coherence auto-configuration starter is added by this change; configure the session and store as application beans.

## Runtime boundary

Java serialization and `ConfigurablePofContext` are supported by the query result conversion bridge.
Other custom serializers fail explicitly rather than silently decoding results with the wrong serializer.
The cluster must use the same Java8 compatibility classes and POF configuration on all storage members.
Interoperability with an unmodified Coherence24.09 server, custom serializers and JSON-B serialization has not been accepted.
The HNSW native binaries are copied from the official24.09 HNSW artifact during the build; the Java17 classes in that artifact are never propagated to the runtime classpath.
Local native execution uses macOS/aarch64; packaged Linux and Windows native resources are checked for presence, not executed here.

## Reproduce

Use JDK17 to build the sources, and select the actual Java8 test JVM:

```bash
./mvnw -pl compatibility/coherence-ai,vector-stores/spring-ai-coherence-store \
  -Pintegration-tests verify -Dcompat.coherence.tests=true \
  -Dit.test=CoherenceJava8IT,CoherenceVectorStoreIT \
  -Dfailsafe.failIfNoSpecifiedTests=false -DreuseForks=false \
  -Djvm=/path/to/jdk8/bin/java -DargLine=-Djava.net.preferIPv4Stack=true
```

The original vector-store integration test uses real MiniLM inference.
It downloads the official model once into `target/coherence-model-cache`, or accepts `-Dcompat.coherence.model=file:/absolute/path/model.onnx` for a verified local copy.
The separate deterministic six-case test exercises actual caches and all indexes across two independent Java8 processes without requiring a model download.
See [UPSTREAM.md](UPSTREAM.md) for source provenance and the Java8 adaptation details.
