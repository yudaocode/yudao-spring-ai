# Boot2 / Java8 Docker Compose support

This module supplies actual Docker/Compose CLI execution, JSON parsing, service discovery, readiness checks, connection-factory events and application-exit lifecycle handling.
It ports44 core/SPI sources from Boot3.1.12, with the3.5.15 multi-name connection predicate and protected constructor needed by current Spring AI factories.
The shared connection-factory registry uses the separate3.5.15 Java8 source port.
The original Boot3 Docker Compose binary is not a runtime dependency.
AOT/native-image support is not provided by this Java8 route.

## Use

Install the `main-jdk8` source tree with JDK17 first; these source snapshots have not been released to Maven Central.
Keep the application's Boot2 parent and import `cn.iocoder.spring-ai:spring-ai-bom:2.1.0-jdk8-SNAPSHOT`.
Add the development library in the appropriate development/test scope:

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai</groupId>
    <artifactId>spring-ai-spring-boot-docker-compose</artifactId>
    <scope>test</scope>
</dependency>
```

Choose a local Compose file and enable Compose for tests when desired:

```properties
spring.docker.compose.file=/absolute/path/compose.yaml
spring.docker.compose.skip.in-tests=false
spring.docker.compose.stop.command=down
```

A Docker daemon and a compatible Docker Compose CLI must be installed.
Native8 acceptance here used Docker24.0.6 and Compose2.23.0-desktop.1.
This is development infrastructure; keep the dependency and configuration out of production runtime unless intentionally needed.
An application can set `spring.docker.compose.enabled=false` to bypass file discovery and startup.
See [the complete installed-artifact consumer](../../compatibility-tests/boot2-docker-compose/README.md).
OpenSearch consumers on a Boot2 parent also need the HTTP5/JSON dependency properties from [the Java8 client instructions](../opensearch-client/README.md).

## Lifecycle contract

The actual Spring application/JVM exit hook stops services started by that application, according to `lifecycle-management` and the configured stop command.
This does not add an extra contract that every `context.close()` stops Compose.
Services which were already running at application startup are retained on exit.
Two real native8 child-process cases validate both ownership scenarios using uniquely named projects, including a Compose path containing spaces and Unicode.
Shutdown/removal assertions run before any explicit test cleanup.

The original upstream test base uses a test-owned `SpringApplicationShutdownHandlers` implementation to execute actual stop callbacks between JUnit classes without casting private Boot2 implementations.
A separate child-process gate exercises the actual global application exit hook.
Neither hook executes global Docker cleanup; test cleanup uses only the generated Compose file/project.

## Source adaptations and boundaries

The port uses `Duration.getSeconds()`, Java8 reader streams and explicit commons-logging level dispatch instead of newer Java/Boot convenience methods.
It removes hard dependencies on unavailable AOT classes while preserving ordinary enabled/test-skip settings.
Both lifecycle and service-connection application listeners are registered.
AI connection factories remain in the Spring AI Compose module; unrelated Boot3 JDBC/database factories are not claimed by this focused port.
Original source licenses/authors and source-artifact hashes are retained in `upstream-source-receipt.json`.

The component gate has23 native8 units: seven original environment cases,15 SPI/image/label/property cases covering all nine AI factory kinds, and one disabled-integration case.
Two real process-lifecycle cases and a standalone installed Boot2 OpenSearch write/search/delete/exit case are separate gates.
All have zero failures/errors/skips with retries disabled.
Live acceptance does not cover every optional backend, cloud account, OS or native-image configuration.
The complete202-module native8 clean install separately passed8121 unit cases, zero failures/errors and17 conditional skips, with retries disabled and frozen Java/POM inputs.
Actual Docker/process/installed-consumer integrations are separate from that unit count.

To run the opt-in lifecycle cases with JDK17 build tools and the actual Java8 test VM:

```bash
./mvnw -pl compatibility/boot-docker-compose -Pintegration-tests verify \
  -Dcompat.devservices.tests=true -Dit.test=Boot2DockerComposeRuntimeIT \
  -Dfailsafe.failIfNoSpecifiedTests=false -Djvm=/path/to/jdk8/bin/java
```
