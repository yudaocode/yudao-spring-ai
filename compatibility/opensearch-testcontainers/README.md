# OpenSearch Testcontainers on Java8

This artifact compiles the official `org.opensearch:opensearch-testcontainers:2.1.3` source against Java8 and Testcontainers1.x.
The original binary is major55 (Java11), although the required source/API is portable to Java8.
It retains the legacy generic `org.opensearch.testcontainers.OpensearchContainer` type used by current Spring AI factories.
The inspected4.1 source renames that type to `OpenSearchContainer`; that breaking API family is not shipped here.

One configuration fix is backported from4.1: preserve an explicitly configured `discovery.type` instead of always forcing `single-node`.
A native8 regression reproduced the older override behavior before the fix.
Four native8 units cover legacy type uniqueness, generic chaining, unsecured defaults, version-dependent initial passwords, explicit passwords and discovery settings.
Real OpenSearch2.19.3 execution is covered by the Spring AI module and standalone Boot2 consumer.

Import the Yudao Spring AI BOM and use `cn.iocoder.spring-ai:spring-ai-opensearch-testcontainers-compat` in test scope.
Do not include the original OpenSearch container jar as well, including version1.0.0: it would provide the same class with a different generic API.
The source build enforces this exclusion.
The source artifact hash, original source hash and final owned source hash are recorded in `upstream-source-receipt.json`.
Upstream Apache2/SPDX notices are retained.
These are locally installed snapshots, not yet Maven Central releases.
