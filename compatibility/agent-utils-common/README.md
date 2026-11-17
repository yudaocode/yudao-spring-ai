# Agent Utils common Java 8 compatibility

Source port of `org.springaicommunity:spring-ai-agent-utils-common:0.13.0` (Apache 2.0).
Original packages, headers and authors are preserved; see `upstream-source-receipt.json`.
This module is included in the default Java 8 reactor and compatibility BOM and is pulled in by `spring-ai-agent-utils-compat`.
The former `agent-utils-compat` profile is an optional command-line alias.
It uses the shared Java8 source generator, native Boot 2 dependencies and Java 8 bytecode guards.
Native JDK 8 tests check TaskCall JSON round trips, required/schema annotation metadata and SubagentDefinition default method execution with Unicode text.
See [the tools module](../agent-utils/README.md) for dependency, Dream and validation details.
