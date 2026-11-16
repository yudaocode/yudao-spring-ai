# Agent Utils Java 8 compatibility

Source port of org.springaicommunity:spring-ai-agent-utils:0.13.0 (Apache 2.0). Original package names, headers and authors are preserved. Source provenance is recorded in upstream-source-receipt.json.

This optional module is built with the agent-utils-compat Maven profile. It uses the same Java8 generator, native framework dependencies and bytecode guards as the compatibility SDK. Compilation and runtime tests are not complete yet.

The optional `agent.dream` subsystem is not compiled: upstream 0.13.0 depends on the former community CrossSessionRecallTools API, which is absent from Spring AI 2.1. Source remains available. This artifact supports the tools/skills/exec/task API used by AI1; it does not advertise the excluded Dream API. Vendored style/header checks are skipped to preserve upstream licensing; dependency and Java8 bytecode guards remain active.
