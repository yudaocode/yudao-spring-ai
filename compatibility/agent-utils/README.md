# Agent Utils Java 8 compatibility

Source port of `org.springaicommunity:spring-ai-agent-utils:0.13.0` (Apache 2.0).
Original packages, source headers and authors are preserved; `upstream-source-receipt.json` records source provenance.
All nine upstream prompt/subagent Markdown resources are packaged verbatim from the official binary; `upstream-resource-receipt.json` records the binary and resource SHA-256 values.

The module is included in the default Java 8 reactor and compatibility BOM.
The former `agent-utils-compat` profile remains a command-line alias; it is no longer required.
Build with JDK 17.0.19+; the Java8 generator emits Java 8 classes and tests run with native JDK 8 using Surefire `-Djvm=/absolute/path/to/jdk8/bin/java`.
Vendored formatting/header checks are disabled to preserve upstream content; Boot 2 dependency and Java 8 bytecode guards remain enabled.

## Usage

Import `cn.iocoder.spring-ai:spring-ai-bom:2.1.0-jdk8-SNAPSHOT` as described in the repository README, then add:

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai</groupId>
    <artifactId>spring-ai-agent-utils-compat</artifactId>
</dependency>
```

Use the original `org.springaicommunity.agent.*` Java imports.
Do not also include the official community Agent Utils or common artifact, because they contain the same classes.
The common compatibility module is transitive.
The application supplies a matching `spring-ai-client-chat` dependency, normally via its model starter.
Dream additionally requires an explicit dependency on `cn.iocoder.spring-ai:spring-ai-session` using the same BOM; it is provided in this artifact so ordinary tools do not force session storage into every application.
These SNAPSHOT coordinates require a local source installation and have not been deployed to Maven Central.

## Dream on the current Spring AI session API

Dream sources are compiled, including memory modes, state and locking.
Upstream 0.13.0 refers to the former `CrossSessionRecallTools` class, which is absent from the current Spring AI source baseline.
The private `DreamSessionRecallTools` adapter uses current `SessionRecallTools` and `SessionService` instead of inventing an obsolete public framework class.

For cross-session recall, configure the Dream builder with a `SessionService` and call `runDreamCycle(memoriesDirectory, principalName)`.
The application must store the same trusted principal name in each session's `SessionAttributes.PRINCIPAL_NAME` metadata.
The principal is fixed by application code and is not a model-supplied tool argument.
Recall rechecks ownership after lookup and only searches matching sessions; it never appends, deletes or compacts history.
It loads the principal's session metadata, orders it by session ID, and searches at most 20 sessions per call.
`nextSessionsPage` continues the session list; `page` and each result's `nextPage` refer to event matches within each session.
This is a current-API adaptation, not a promise of binary compatibility with the removed legacy recall class.
Without the service or principal, Dream still supports memory-only execution.

## Validation

Native JDK 8 tests cover real loopback HTTP and charset decoding, record JSON/schema metadata, interface default methods, reflective recall tool invocation, principal isolation, read-only history access, session/event pagination, overflow, packaged default resources, and a Dream cycle with a local mock model, persisted state and lock release.
No paid model or remote user history is used by these tests.
A successful unit run is separate from provider/service integration acceptance.
Java 8 Javadoc packaging is skipped by the build command because the vendored modern source syntax is lowered only for compilation.

The two-module native8 suite passed11 tests with no failures/errors/skips.
The complete193-module default build then passed8042 cases with17 conditional skips and no failures/errors.
The separate [Boot2 consumer](../../compatibility-tests/boot2-agent-utils) passed one real local HTTP Dream/recall case with an actual in-memory session store, including an attempted cross-user fixture and unchanged history versions.
These gates are recorded separately.
