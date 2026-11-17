# Boot2 / Java8 installed Compose consumer

This standalone Boot2.7.18 project consumes only installed Yudao Spring AI and Compose bridge jars.
It uses a real Java8 parent/child process, a unique Compose project and an actual OpenSearch2.19.3 server.
The child verifies single jar providers, actual Boot2 version, automatic connection details, real vector write/filter/score/delete and application-exit project removal.
Deterministic embeddings use the actual default Spring AI batch embedding path; no paid model is called.
The separate original source-module Testcontainers integration retains real MiniLM inference.

First install the parent/BOM, Spring AI OpenSearch starter and [Compose modules](../../compatibility/boot-docker-compose/README.md).
Then run:

```bash
./mvnw -f compatibility-tests/boot2-docker-compose/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java
```

Docker and Compose are required, with the OpenSearch2.19.3 image available locally or pullable.
The file path contains spaces/Unicode to exercise process argument handling.
Its health check waits for the owned OpenSearch server to be ready.
The intentionally unreachable URI property prevents accidental fallback to a user's default localhost9200 service if SPI registration fails.
The generated project has its own label/name; both application shutdown and final cleanup affect that project only.
The assertion for automatic removal occurs before explicit cleanup.
