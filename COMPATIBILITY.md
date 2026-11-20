# Boot 4 compatibility branch

This branch follows upstream Spring AI main at 4cce4caa9653092624b20ae1113633b3ad97d256,
using Spring Boot 4.1.2 and version 2.1.1-jdk25-SNAPSHOT. It targets the ruoyi-vue-pro-sb4
consumer, whose actual runtime is Java 25. The library itself retains upstream Java 17 bytecode.

main-jdk17 targets Boot 3 and Java 17; main-jdk8 targets Boot 2 and Java 8.
The Boot 2 port and cross-consumer alignment are still in progress. No release is published.

The model module retains a legacy ZhiPu assistant message type solely to migrate Alibaba
persisted graph states onto ordered message parts. It does not restore the removed provider client.

Local model inference tests use a shared, SHA-256 verified cache of a pinned upstream Git LFS model.
No hosted model credential is required by those tests.

Progress and validation evidence are maintained in:
/Users/yunai/Java/yudao-all-in-vben/project_duibiao/springai

## 验证范围

- 上游基准：`4cce4caa9653092624b20ae1113633b3ad97d256`。本分支版本为 `2.1.1-jdk25-SNAPSHOT`，对应 Boot `4.1.2`，最低目标 JDK `17+`，消费验证 JVM `25`。使用 `cn.iocoder.spring-ai`，保留原 artifactId 和 Java 包名。
- 默认 reactor：Boot 2 当前为202模块，Boot 3/4为184模块。默认单元测试、依赖守卫和本地安装通过完整运行及后续按模块/测试类闭合；不冒充每次全量运行都一次通过。Boot 3/4库级全量测试使用JDK17，Java8分支用实际JVM8；实际业务消费另在JDK8/17/25验证。
- 三套冻结业务快照跑通启动、同步/SSE聊天、Token入库、图片生成下载、知识库解析/异步切片/带元数据过滤检索/删除。AI1另有各301项按最新类结果合并覆盖，包含隔离Redis恢复验证。外部模型和向量服务并非全部在线验证。
- 三套共同HTTP回归各10项通过，覆盖OpenAI、Azure Embedding、原生Gemini、通义Embedding/图片及应用ObservationRegistry、思考内容增量。相应服务层与工具类回归在实际JVM8/17/25分别51/54/61项通过。
- Tika整族分别为Boot2=2.9.4、Boot3=3.3.1、Boot4=4.0.0；BOM仅管理Tika自身组件，避免父BOM覆盖应用框架依赖。
- Java8分支已纳入Agent Utils/Dream、Coherence向量源码兼容模块、Testcontainers和Docker Compose普通JVM开发辅助模块；AOT/native-image不在Java8支持范围。具体依赖及使用边界以[main-jdk8兼容说明](https://github.com/yudaocode/yudao-spring-ai/blob/main-jdk8/COMPATIBILITY.md)为准。
- 消费项目需要适配Spring AI 2.x API。既有平台存储值、业务新增功能及Boot/JDK原生类型仍可不同，不能把“能够编译运行”理解为三个业务仓库源码完全相同。当前为开发兼容分支，后续扩展需补实际目标JVM验收。

## Standalone AWS OpenSearch starter dependency closure

The AWS starter now declares sdk-core/auth/regions/http-auth-aws explicitly.
Previously its apache-client dependency alone did not supply credentials/region classes; the standalone consumer failed to compile without them.
The native Boot4.1.2 fixture depends only on the AWS starter and test starter, not on another model provider that could accidentally supply those classes.
The same installed producer artifacts passed2 tests on native17 and2 on native25, zero failures/errors/skips: actual transport bean creation and local SigV4 signing with mocked HTTP execution.
No cloud/model service acceptance or new full184-module test run is claimed.
See [the consumer](compatibility-tests/aws-opensearch) for use and runtime selection.

## Exact cosine scores with OpenSearch 2.19

The three compatibility routes expose the same setting:

```yaml
spring:
  ai:
    vectorstore:
      opensearch:
        cosine-similarity-score-boost: 1.0
```

Or call `OpenSearchVectorStore.builder(client, model).cosineSimilarityScoreBoost(1.0f)`.
Use this for engines such as the tested OpenSearch2.19.3 which already normalize exact cosine script scores to0..1.
The legacy default0.5 is retained for older engines that return0..2; approximate k-NN and other distance spaces are unchanged.
The multiplier must be positive and finite; invalid values fail binding/building.
No extra cluster-info call or privilege is required to choose this setting.

A real2.19.3 engine reproduced an identical-vector score0.5 before the fix.
After explicit configuration, exact and approximate cosine both return1.0 and retain the document at threshold0.9, including through Boot auto-configuration.
Boot2/JDK8 consumer7 cases passed; Boot3 and Boot4 each passed4 cases on native17 and25, all zero failures/errors/skips.
The32 OpenSearch module units passed in all three source routes (native8,17,17 respectively); broader dependency closures are separate checkpoints.
An SSE keep-alive test now accepts a comment already queued during graceful close while still asserting the final RPC result and timely completion.
Its39 cases passed on Boot2/native8 and Boot3/4 ×native17/25 with retry disabled.
No real AWS account was called; the engine is a local test container and AWS signing uses a dummy-credential local fixture.


## Java8 route checkpoint synchronization

The separate Boot2/Java8 route now has202 default modules, including Coherence vector support, Testcontainers and Docker Compose compatibility sources.
Its native8 clean install passed8121 unit cases, zero failures/errors and17 conditional skips, with retries disabled and frozen Java/POM inputs.
Focused real service/child-process/installed-consumer integrations are separately recorded on that branch.
This paragraph synchronizes cross-route documentation; it does not claim a new full184-module run for this modern branch.
See [main-jdk8 usage, provenance and boundaries](https://github.com/yudaocode/yudao-spring-ai/blob/main-jdk8/COMPATIBILITY.md).
