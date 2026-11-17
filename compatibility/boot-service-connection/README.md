# Java8 connection-factory registry

This module ports four Spring Boot3.5.15 service-connection factory/exception sources onto Spring5 and the shared `org.springframework.ai.util.ConnectionDetails` marker.
The original Boot3 auto-configuration binary is not a runtime dependency.
It is shared by the Boot2 development-service adapters.

Spring5's `SpringFactoriesLoader.loadFactoryNames` and `BeanUtils` replace the Spring6 factory loader.
Optional provider class/SDK failures are logged and skipped, preserving the upstream failure-handler behavior; failures when a matching factory creates its actual connection details remain visible.
The registry retains Spring ordering, source-type filtering, required/optional result semantics, immutable results and duplicate-type rejection.
Nine native8 unit cases cover these contracts, including a missing optional SDK wrapped by `BeanInstantiationException`.
The Testcontainers bridge additionally exercises factories in real Boot2 contexts and real containers.

Use this through the Spring AI test/development adapters rather than adding original Boot3 service-connection jars.
See [Testcontainers usage](../boot-testcontainers/README.md) and `upstream-source-receipt.json` for source provenance.
Native-image/AOT is not supplied by this Java8 module.
