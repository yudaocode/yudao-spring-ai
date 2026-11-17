# Installed-artifact Boot2 / Java8 service-connection consumer

This standalone project has a Boot2.7.18 parent and consumes installed Yudao Spring AI jars.
It does not include producer source or `target/classes` directories.
Install the parent, BOM and the [Testcontainers compatibility modules](../../compatibility/boot-testcontainers/README.md) first.
The POM includes the HTTP5/JSON version properties needed by the Boot2 parent.

```bash
./mvnw -f compatibility-tests/boot2-testcontainers/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java
```

A local Docker daemon and `opensearchproject/opensearch:2.19.3` are required.
The test verifies actual Java8, one installed provider for each bridge type, service-connection precedence over an intentionally unreachable property URI, real index creation, Unicode, filter/score/delete behavior and automatic container shutdown on context close.
It uses deterministic local embeddings; the original source-module integration test separately retains real MiniLM inference.
The container has an owned task label and cleanup only touches that container.
The second native8 case checks SLF4J MDC/Marker and Testcontainers' shaded Guava cache/hash/comparator/future fallback paths identified during dependency auditing.
No paid provider or remote account is called.
