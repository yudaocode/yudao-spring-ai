# Coherence source provenance

The vector/HNSW source baseline is [Oracle Coherence24.09](https://github.com/oracle/coherence/tree/e7efd86662374c5b9bb20b621c3d2e8f5275b5f6), commit `e7efd86662374c5b9bb20b621c3d2e8f5275b5f6`.
The runtime core is the original `com.oracle.coherence.ce:coherence:14.1.1-0-29` jar.
The native resource source is `com.oracle.coherence.ce:coherence-hnsw:24.09`.

This module contains19 upstream AI sources,15 HNSW sources and the `PofConfigProvider` interface, preserving upstream UPL headers and authors.
Five private Java8 helpers carry the Apache2 license and the Yudao author credit.
The UPL license is supplied as [LICENSE-COHERENCE.txt](LICENSE-COHERENCE.txt).
`upstream-source-receipt.json` records the source baseline, original source hashes and final owned source hashes.

The shared AST source generator rewrites modern Java syntax to Java8 sources and directs helper references to this module's private package.
Its optional third argument does not alter existing two-argument consumers.
Jabel compiles the resulting sources with `--release 8`; dependency bytecode and Boot2 dependency exclusion checks remain enabled.
Source style checks are disabled only for this vendor module to preserve its upstream format and licenses.
The Spring AI store retains its ordinary checks.

Additional source adaptations are:

- Replace newer classloader and binary-entry convenience APIs with equivalent core14 APIs.
- Add the JavaBean metadata getter required by core14's extractor without changing the Spring AI filter property names.
- Use member-level aggregation because core14 indexes span all local partitions.
- Read legacy backing-map index maps and use core14's two-argument POF primitive-array writers.
- Bridge the older no-argument result finalizer with the actual cache serializer, carrying the Java/POF descriptor through remote aggregation and `supply()`.
- Include an explicit Java8 POF configuration because core14 does not load newer POF-provider service declarations.
- Adapt the Spring store's private cache type from `NamedMap` to core14's `NamedCache`; retain its public `Session` signatures.

The serialized aggregator has an added serializer descriptor and is a compatibility cluster type, not a claim of wire compatibility with an unmodified24.09 cluster.
The HNSW native payload remains upstream; only Java source is adapted.
