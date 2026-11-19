<!-- YUDAO-COMPATIBILITY-INTRO-BEGIN -->

> 版本命名：保留当前源码基线的官方版本号；共用现代制品不加 JDK 后缀，隔离维护的兼容路线添加 `jdk8` / `jdk17` / `jdk25`。开发版本使用 SNAPSHOT。JDK 路线后缀不等于最低字节码版本，框架依赖仍按 Boot 版本选择。
> 完整版本规则与预览命名见 [VERSIONING.md](VERSIONING.md)。

# yudao-spring-ai

yudao-spring-ai 是 [Spring AI](https://github.com/spring-projects/spring-ai) 的芋道社区兼容维护版本，并非官方发布。主要为 **Spring Boot 2.x、Spring Boot 3.x** 项目回迁较新版本 Spring AI 的能力；`main` 保留 Spring Boot 4.x 版本线。

## 仓库与版本线

| 平台 | 仓库地址 |
| --- | --- |
| Gitee | [yudao-spring-ai](https://gitee.com/yudaocode/yudao-spring-ai) |
| GitHub | [yudao-spring-ai](https://github.com/yudaocode/yudao-spring-ai) |

| 分支 | 已验证 Spring Boot | 运行 JDK | 源码开发版本 | Maven Central |
| --- | --- | --- | --- | --- |
| [`main`](https://github.com/yudaocode/yudao-spring-ai/tree/main) | 4.1.2 | 17+（业务消费使用25） | `2.1.0-jdk25-SNAPSHOT` | [2.1.0-jdk25-preview-20261119](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk25-preview-20261119) |
| [`main-jdk17`](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk17) | 3.5.15 | 17+ | `2.1.0-jdk17-SNAPSHOT` | [2.1.0-jdk17-preview-20261118](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk17-preview-20261118) |
| [`main-jdk8`](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk8) | 2.7.18 | 8+ | `2.1.0-jdk8-SNAPSHOT` | [2.1.0-jdk8-preview-20261119](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk8-preview-20261119) |

版本保留上游源码基线编号，以 JDK 路线隔离 Boot 依赖；发布时追加北京时间日期，不增加同日序号。当前分支：**`main`**。

Maven groupId 为 **`cn.iocoder.spring-ai`**，artifactId 和 Java 包名沿用官方。同一个应用只选择一条版本线，同一项目的模块使用同一版本；替换已有依赖时，也要移除对应官方 groupId 下的同类模块，避免重复类。

Boot 4 / JDK 25 路线已发布 [2.1.0-jdk25-preview-20261119](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk25-preview-20261119)。宿主可以直接使用下面的 Maven 坐标，也可以按需安装源码开发版 `2.1.0-jdk25-SNAPSHOT`。 发布版本按 `官方版本[-jdk数字][-preview]-YYYYMMDD` 命名；共用制品省略 JDK，未发布上游基线增加 preview，无同日序号。

## 如何使用

Maven 示例直接使用公开制品。需要构建 `2.1.0-jdk25-SNAPSHOT` 时，使用 JDK 17.0.19+ 执行以下源码安装命令。

```bash
git clone -b main https://github.com/yudaocode/yudao-spring-ai.git
cd yudao-spring-ai
bash ./mvnw -DskipTests -Dmaven.javadoc.skip=true install
```

宿主保留自己的 Spring Boot parent/BOM。下面以 OpenAI starter 为例，其他模型按需选择对应 starter：

```xml
<properties>
    <yudao-spring-ai.version>2.1.0-jdk25-preview-20261119</yudao-spring-ai.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iocoder.spring-ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${yudao-spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>cn.iocoder.spring-ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>
```

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

配置环境变量后，注入 `ChatModel` 或 `ChatClient.Builder` 使用；Java 导入仍为 `org.springframework.ai.*`。例如注入名为 `builder` 的 `ChatClient.Builder` 后，调用 `builder.build().prompt("你好").call().content()`。

AWS OpenSearch starter 已补齐独立使用所需的 SDK core、认证、区域和签名依赖。原生 Boot 4.1.2 父工程仅引入该 starter，在 JDK17、JDK25 上各通过 2 项客户端自动配置/本地 SigV4 签名测试；示例见 [独立消费验证](compatibility-tests/aws-opensearch)。未调用真实 AWS 服务。

详细构建、源码基线、API 差异及当前支持边界见 [COMPATIBILITY.md](COMPATIBILITY.md)。

下方逐字保留官方项目原始 README，其中的官方 Maven 坐标和发布状态仅针对上游项目；使用本兼容版时，以本节坐标、版本线和支持范围为准。

OpenSearch2.19.3 的精确余弦评分已补三线统一配置：设置 `spring.ai.vectorstore.opensearch.cosine-similarity-score-boost=1.0`，或 builder 的 `cosineSimilarityScoreBoost(1.0f)`。
保留旧引擎默认0.5，近似检索及其他距离不变。
真实引擎已验证相同向量得分1.0与0.9阈值；Boot2/JDK8消费7项、Boot3/4在JDK17/25各4项均零失败/错误/跳过。
完整用法、验证边界见 [COMPATIBILITY.md](COMPATIBILITY.md#exact-cosine-scores-with-opensearch-219)。

Boot2/Java8 路线已纳入 Coherence 向量库、Testcontainers、Docker Compose 兼容模块，当前默认202模块原生JDK8完整构建通过8121项单测，零失败/错误、17项条件跳过。
各模块用法、真实服务分项验证及支持边界见 [main-jdk8 说明](https://github.com/yudaocode/yudao-spring-ai/blob/main-jdk8/COMPATIBILITY.md)；本段仅同步跨分支状态，不代表本现代分支重新全量测试。

Docker 真库补验在 Spring Boot 4.1.2 的 JDK 17 和 25 上各通过 236 项，均零失败、零跳过。JDBC Session 覆盖 MySQL、PostgreSQL 和 MariaDB 共 178 项，Redis Stack Chat Memory 覆盖 58 项。

SQL Server 2017 和 2022 的 Docker 复验在本分支 JDK 17 和 25 分别通过 74 项，覆盖 JDBC Session 70 项和自动配置 4 项。测试可通过 `-Dspring.ai.test.sqlserver.image` 覆盖镜像，默认仍为 SQL Server 2022。

Boot 4.1.2 在 JDK 17.0.21 和 25.0.3 各通过 50 项聊天记忆 Docker 测试。Cassandra 5.0 为 9 项、MongoDB 8.0.6 为 10 项、Neo4j 5 为 31 项，均零跳过。Cassandra 集成夹具为 schema 初始化提供 10 秒请求超时。

Boot 4.1.2 / JDK 17.0.21、25.0.3 的 MCP WebFlux Docker 回归每种 JVM 通过 200 项，覆盖 SSE 和 Streamable HTTP 的同步及异步客户端。2 项请求定制公共测试按原有能力条件跳过。测试服务器使用 Node 26 与 MCP server-everything 2025.12.18。

Ollama 0.40.9 的 ARM64 Docker 服务在 Boot 4.1.2 / JDK 17.0.21、25.0.3 每种 JVM 通过 2 项向量用例，使用 `nomic-embed-text` 和 `all-minilm`，覆盖批量向量、维度和附加模型管理，零跳过。

Ollama 0.40.9 的 ARM64 Docker 容器使用 `qwen3-embedding:8b`，在 Boot 4.1.2 / JDK 17.0.21、25.0.3 每种 JVM 通过 9 项集成测试。
向量默认返回 4096 维，`dimensions` 可指定为 512 或 1024。
使用 512 token 上下文时，`truncate=true` 对超长输入返回向量，`truncate=false` 返回 HTTP 400，短输入可正常编码。

<!-- YUDAO-COMPATIBILITY-INTRO-END -->

---

# Spring AI [![build status](https://github.com/spring-projects/spring-ai/actions/workflows/build-and-deploy-snapshot.yml/badge.svg)](https://github.com/spring-projects/spring-ai/actions/workflows/build-and-deploy-snapshot.yml) [![Maven Central](https://img.shields.io/maven-central/v/org.springframework.ai/spring-ai-model?label=Maven%20Central&versionPrefix=2.0)](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-model)

The Spring AI project provides a Spring-friendly API and abstractions for developing AI applications.

Its goal is to apply Spring ecosystem design principles, such as portability and modular design, to the AI domain and promote using strongly-typed data structures and APIs as the building blocks of an application.

![spring-ai-integration-diagram-3](https://docs.spring.io/spring-ai/reference/_images/spring-ai-integration-diagram-3.svg)

At its core, Spring AI addresses the fundamental challenge of AI integration: connecting your enterprise __Data__ and __APIs__ with the __AI Models__.

## Getting Started

The [reference documentation](https://docs.spring.io/spring-ai/reference/) includes a [Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html) guide.

Spring Boot Version Compatibility:
* **Spring AI 2.x.x** ([main](https://github.com/spring-projects/spring-ai/tree/main) branch) - Spring Boot `4.x`
* **Spring AI 1.1.x** ([1.1.x](https://github.com/spring-projects/spring-ai/tree/1.1.x) branch) - Spring Boot `3.5.x`

## Project Resources

* [Reference documentation](https://docs.spring.io/spring-ai/reference/)
* [Javadoc](https://docs.spring.io/spring-ai/docs/current/api/)
* [Upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
* [Awesome Spring AI](https://github.com/spring-ai-community/awesome-spring-ai) - A curated list of awesome resources, tools, tutorials, and projects for building generative AI applications using Spring AI
* [Spring AI Examples](https://github.com/spring-projects/spring-ai-examples) - Contains example projects that explain specific features in more detail.
* [Spring AI Community](https://github.com/spring-ai-community) - A community-driven organization for building Spring-based integrations with AI models, agents, vector databases, and more.

## Contributing

We welcome contributions of all kinds!
Please read our [contribution guidelines](CONTRIBUTING.md) before submitting a pull request or an issue.

## Features

This is a high level feature overview.

* Support for all major [AI Model providers](https://docs.spring.io/spring-ai/reference/api/index.html) such as Anthropic, OpenAI, Amazon Bedrock, Google, Ollama, Mistral AI, DeepSeek, and more.
  Supported model types include:
  - [Chat](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
  - [Embedding](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
  - [Text to Image](https://docs.spring.io/spring-ai/reference/api/imageclient.html)
  - [Audio Transcription](https://docs.spring.io/spring-ai/reference/api/audio/transcriptions.html)
  - [Text to Speech](https://docs.spring.io/spring-ai/reference/api/audio/speech.html)
  - [Moderation](https://docs.spring.io/spring-ai/reference/api/index.html#api/moderation)
* Portable API support across AI providers for both synchronous and streaming options.
  Access to [model-specific features](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_chat_options) is also available.
* [Structured Outputs](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) - Mapping of AI Model output to POJOs.
* Support for all major [Vector Store providers](https://docs.spring.io/spring-ai/reference/api/vectordbs.html) such as *Amazon Bedrock Knowledge Base, Amazon S3, Apache Cassandra, Azure Vector Search, Chroma, Couchbase, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, Typesense, and Weaviate*.
* Portable API across Vector Store providers, including a novel SQL-like [metadata filter API](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#metadata-filters).
* [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) - Permits the model to request the execution of client-side tools and functions, thereby accessing necessary real-time information as required.
* [Observability](https://docs.spring.io/spring-ai/reference/observability/index.html) - Provides insights into AI-related operations.
* Document injection [ETL framework](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html) for Data Engineering.
* [AI Model Evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html) - Utilities to help evaluate generated content and protect against hallucinated response.
* [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) - Fluent API for communicating with AI Chat Models, idiomatically similar to the WebClient and RestClient APIs.
* [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html) - Encapsulates recurring Generative AI patterns, transforms data sent to and from Language Models (LLMs), and provides portability across various models and use cases.
* [MCP (Model Context Protocol)](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html) - First-class support via Boot Starters and MCP Java Annotations for building AI applications that consume MCP servers or expose Spring-based services to the AI ecosystem, with STDIO, SSE, and Streamable-HTTP transport support.
* Support for [Chat Conversation Memory](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_chat_memory) with pluggable persistent backends (JDBC, Cassandra, MongoDB, Neo4j, Redis) and [Retrieval Augmented Generation (RAG)](https://docs.spring.io/spring-ai/reference/api/chatclient.html#_retrieval_augmented_generation).
* Spring Boot Auto Configuration and Starters for all AI Models and Vector Stores - use [start.spring.io](https://start.spring.io/) to select the Model or Vector Store of choice.

## Building from source

You don’t need to build from source to use Spring AI.
If you want to try out the latest and greatest, Spring AI can be built and published to your local Maven repository:
```shell
./mvnw clean install
```
This command builds all modules, runs unit tests, and publishes artifacts to your local Maven repository.

Please read our [contribution guidelines](CONTRIBUTING.md) for more details.
