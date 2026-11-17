# Boot 2 / Java 8 兼容说明

本分支基于相同上游 main 基准和已验证的 Boot 3 源码回迁，使用 Boot 2.7.18、Spring 5.3、Jackson 2、Spring Retry 1.3.4。
版本为 `2.1.0-jdk8-SNAPSHOT`，Maven groupId 使用 `cn.iocoder.spring-ai`。
默认 SDK reactor 已通过分段库级门禁；消费者完整迁移仍在进行。已验证范围与排除项见下文。

## 构建与运行 JVM

构建工具使用 JDK 17.0.19+；Jabel 只处理语言语法，编译器以 `--release 8` 校验平台 API，并输出 Java 8 字节码。
源码转换在 target 内生成普通类，保留上游 record 源码便于同步。
构造校验、JSON、必填字段、浮点/数组相等性和不可变集合行为由实际测试验证。
构建细节见 [Java 8 构建说明](compatibility/java8/README.md)。

使用 JDK 17 启动 Maven，显式指定真实 JDK 8 运行测试：

```bash
export JAVA_HOME=/path/to/jdk17
export JAVA8_HOME=/path/to/jdk8
./mvnw -Djvm="$JAVA8_HOME/bin/java" clean package
```

Java8CompatibilityTests 和 RestClientJava8Tests 会断言实际运行 VM 为 1.8。
只修改 source/target 或在 JDK 17 上运行测试不视为 Java 8 验收。
依赖 Enforcer 检查 Boot 2 / Spring 5 主版本、命名空间、传递依赖和字节码上界；默认190模块库级门禁已分段通过；不包含下文明确排除的可选模块。

## 框架兼容模块

- [JSpecify 注解兼容](compatibility/jspecify/README.md)：保留运行期空值语义，移除 Java 8 不认识的 MODULE 注解 target；不得同时携带原注解 jar。
- [Spring Web 兼容](compatibility/spring-web/README.md)：使用 Spring 5 HTTP 实现回迁 RestClient 新 API，保留官方许可、作者及来源指纹；不替换 Spring 5 已有类，只用于 Boot 2。
- Spring 6 AOT/Native Image 注册基础设施不包含在 Boot 2 普通 JVM 产物中；Java 8 无 sealed 类型检查/反射。工具参数增强支持普通结构化 DTO。
- 保留 Spring AI 2.1 的模型/消息/工具 API，不能承诺与 1.1 调用源码完全兼容。旧 ZhiPu assistant 消息类型只为 Alibaba 图状态迁移保留，不恢复旧 provider。

当前验证记录在用户指定的长期笔记目录。
未向 GitHub/Gitee 或 Maven 远端发布。

## 验证范围

- 上游基准：`4cce4caa9653092624b20ae1113633b3ad97d256`。本分支版本为 `2.1.0-jdk8-SNAPSHOT`，对应 Boot `2.7.18`，最低目标 JDK `8`，消费验证 JVM `8`。使用 `cn.iocoder.spring-ai`，保留原 artifactId 和 Java 包名。
- 默认 reactor：Boot 2 为190模块，Boot 3/4为184模块。默认单元测试、依赖守卫和本地安装通过完整运行及后续按模块/测试类闭合；不冒充每次全量运行都一次通过。Boot 3/4库级全量测试使用JDK17，Java8分支用实际JVM8；实际业务消费另在JDK8/17/25验证。
- 三套冻结业务快照跑通启动、同步/SSE聊天、Token入库、图片生成下载、知识库解析/异步切片/带元数据过滤检索/删除。AI1另有各301项按最新类结果合并覆盖，包含隔离Redis恢复验证。外部模型和向量服务并非全部在线验证。
- 三套共同HTTP回归各10项通过，覆盖OpenAI、Azure Embedding、原生Gemini、通义Embedding/图片及应用ObservationRegistry、思考内容增量。相应服务层与工具类回归在实际JVM8/17/25分别51/54/61项通过。
- Tika整族分别为Boot2=2.9.4、Boot3=3.3.1、Boot4=4.0.0；BOM仅管理Tika自身组件，避免父BOM覆盖应用框架依赖。
- Java8分支不将Coherence、Boot3 devservices等受限可选模块计入默认支持；Agent Utils 0.13.0的common/tools两模块现纳入默认构建与BOM，Dream已按当前Session API适配，包含官方默认提示词资源；跨会话回忆要求应用写入一致的principal元数据，见[模块说明](compatibility/agent-utils/README.md)。具体依赖、模块和API边界见本文件对应说明及compatibility目录。
- 消费项目需要适配Spring AI 2.x API。既有平台存储值、业务新增功能及Boot/JDK原生类型仍可不同，不能把“能够编译运行”理解为三个业务仓库源码完全相同。当前为开发兼容分支，后续扩展需补实际目标JVM验收。

## Redis public API migration

This older-Boot branch uses `redis.clients.jedis.JedisPooled` in the public builders for `RedisVectorStore`, `RedisChatMemoryRepository` / `RedisChatMemoryConfig`, and `DefaultSemanticCache` / `RedisVectorStoreHelper`. Upstream Spring AI 2.1 and the Boot 4 `main` branch use `RedisClient` in these positions. Applications constructing these low-level objects must supply the native branch's `JedisPooled`; an upstream `RedisClient` bean cannot be passed unchanged. High-level Spring AI interfaces remain the preferred integration boundary. This deliberate dependency adaptation is not a claim of identical low-level Redis APIs across branches.

## ITU duration parsing on Java 8

`spring-ai-commons` supplies `spring-ai-itu-compat` (ITU 1.17.0 sources compiled with `--release 8`). The chat client excludes the original ITU jar. Its original major-52 binaries call a Java 9-only Math overload when parsing durations; native Java 8 regression tests cover duration/date parsing and overflow. See [provenance](compatibility/itu/UPSTREAM.md). Do not include the original `com.ethlo.time:itu` alongside this compatibility artifact.

## Agent Utils and current native8 gate

The default193-module clean install passed8042 XML test cases on native Java8, zero failures/errors and17 conditional skips.
Agent Utils common/tools account for11 cases including the now-compiled Dream subsystem.
A separate native Boot2.7.18 consumer passed one end-to-end Dream tool-call case through actual local HTTP, real session storage, principal isolation and persisted state.
See `compatibility-tests/boot2-agent-utils` for a reproducible source fixture.

The expanded runtime scan covered835 jars /270702 effective base classes, all <=major52.
Static API scanning remains a separate gate: besides optional modern-VM/Flow/SLF4J fallback branches in third-party libraries, it identified MCP's duplicate original ITU1.14 and OpenSearch2.23 PercentCodec's covariant ByteBuffer.flip call.
The MCP finding is resolved by the follow-up below; the OpenSearch follow-up below resolves the separate codec/transport linkage issues.
The new Agent Utils classes introduce no missing Java8 API references in that scan.

## MCP ITU dependency follow-up

The duplicate original ITU1.14 dependency is now excluded from the MCP Jackson2 schema module; the existing shared ITU1.17 Java8 source port remains the sole provider.
The parent native Boot2 dependency rule rejects com.ethlo.time:itu in any module.
The new class-resource uniqueness regression failed with two implementations before the fix and passes afterward; real NetworkNT duration-format valid/invalid assertions also pass on native8.
The affected15-module native8 test gate passed2333 cases, zero failures/errors, 2 conditional skips, and all193 modules passed dependency validation.
This is a follow-up component/dependency gate after the full8042-case checkpoint, not a new full193-module test run.
The refreshed runtime audit removes the original ITU jar entirely.
OpenSearch's separate codec issue was reproduced on native8 and is resolved by the later3.11 follow-up below.

## OpenSearch3.11 follow-up

The Java8 default reactor expands to194 modules with `spring-ai-opensearch-client-compat`.
The original3.11 jar has6631 effective base classes but links two Java9-only ByteBuffer method descriptors.
Only PercentCodec and ApacheHttpClient5Transport source families are recompiled against Java8;6610 other SDK entries are retained unchanged and verified independently.
The compatibility jar preserves all6631 base classes, including the separately named transport builder.
The provided original jar is not propagated into applications; the standalone consumer requires exactly one SDK class resource.

HTTP5/Core5 align at5.6.5/5.4.4, retaining the existing Jackson2.21.4/Reactor3.7.19/Jakarta JSON2.1.3 native8 stack.
AWS starters now carry missing core/auth/region/signer dependencies, and the Java8 AWS starter includes the SSL facade.
The native Boot2 parent fixture explicitly sets namespace version properties documented in the module README.

Complete194-module native8 clean install:8052 cases,0 failures/errors,17 conditional skips.
After that full checkpoint, the new static SDK JDK-symbol regression was added; the SDK component then passed9 native8 cases, including both HTTP modes.
The standalone Boot2 consumer passed5 cases using the AWS starter alone, covering two vector-query modes, AWS client bean creation and actual SigV4 signing with a local mocked transport, and an optional JSON-B mapper round trip.
The earlier ordinary-starter consumer passed3 cases separately.
These are separate gates; no new full8053-case one-shot run or real OpenSearch/AWS/model account acceptance is claimed.

The installed SDK also passed static member-linkage checks against the actual Boot2 consumer dependency classpath (HTTP5/Jackson2/JSON/AWS/Reactor), with zero missing references.
