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
| [`main`](https://github.com/yudaocode/yudao-spring-ai/tree/main) | 4.1.2 | 17+（业务消费使用25） | `2.1.0-jdk25-SNAPSHOT` | 待发布 |
| [`main-jdk17`](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk17) | 3.5.15 | 17+ | `2.1.0-jdk17-SNAPSHOT` | [2.1.0-jdk17-preview-20261118](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk17-preview-20261118) |
| [`main-jdk8`](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk8) | 2.7.18 | 8+ | `2.1.0-jdk8-SNAPSHOT` | [2.1.0-jdk8-preview-20261119](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk8-preview-20261119) |

版本保留上游源码基线编号，以 JDK 路线隔离 Boot 依赖；发布时追加北京时间日期，不增加同日序号。当前分支：**`main`**。

Maven groupId 为 **`cn.iocoder.spring-ai`**，artifactId 和 Java 包名沿用官方。同一个应用只选择一条版本线，同一项目的模块使用同一版本；替换已有依赖时，也要移除对应官方 groupId 下的同类模块，避免重复类。

Boot 3 / JDK 17 已发布 [2.1.0-jdk17-preview-20261118](https://central.sonatype.com/artifact/cn.iocoder.spring-ai/spring-ai-bom/2.1.0-jdk17-preview-20261118)，使用方式见 [main-jdk17](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk17)。本分支的 Boot 4 示例使用源码开发版，需先安装到本地仓库。发布版本按 `官方版本[-jdk数字][-preview]-YYYYMMDD` 命名；共用制品省略 JDK，未发布上游基线增加 preview，无同日序号。

## 如何使用

使用 JDK 17.0.19+ 构建源码；Java 8 分支同样使用 JDK 17 构建，产物供 JDK 8 运行。

```bash
git clone -b main https://github.com/yudaocode/yudao-spring-ai.git
cd yudao-spring-ai
bash ./mvnw -DskipTests -Dmaven.javadoc.skip=true install
```

宿主保留自己的 Spring Boot parent/BOM。下面以 OpenAI starter 为例，其他模型按需选择对应 starter：

```xml
<properties>
    <yudao-spring-ai.version>2.1.0-jdk25-SNAPSHOT</yudao-spring-ai.version>
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
