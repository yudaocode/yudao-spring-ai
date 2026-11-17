# MCP Jackson2 Java8 runtime

This module keeps NetworkNT JSON Schema Validator2.0.8 and excludes its original com.ethlo.time:itu dependency.
The shared spring-ai-commons dependency supplies our Java8-safe ITU1.17 source port.
Do not add an original ITU jar alongside it: duplicate classes make runtime selection depend on classpath order, and the original duration parser calls a Java9-only Math overload despite Java8 class-file headers.
Native8 regression tests require exactly one ITU class resource and exercise real NetworkNT duration-format validation with format assertions enabled.
The two new tests and the affected MCP module suites pass on actual JDK8; the complete193-module dependency guard also rejects any reintroduced original ITU artifact.
