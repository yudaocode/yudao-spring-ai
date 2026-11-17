# Boot2 / native Java8 Coherence consumer

Install the parent, BOM, Coherence compatibility module and store first.
This standalone Boot2.7.18 project has no reactor parent and uses only installed dependency jars.

```bash
./mvnw -f compatibility-tests/boot2-coherence/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java \
  -DargLine=-Djava.net.preferIPv4Stack=true
```

The test asserts the real Java8 VM, unique jar providers, a real Boot context, POF storage and all three vector indexes.
It verifies Unicode, metadata filtering, score thresholds, deletion and the native `Session` contract.
Embeddings are deterministic local vectors; no paid model or cloud service is called.
See [the SDK usage and boundaries](../../compatibility/coherence-ai/README.md).
