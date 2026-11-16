# Boot 3 兼容说明

本分支以 Spring Boot `3.5.15` 的依赖管理为基准，所有 Spring、Jackson、Spring Data、测试容器依赖遵守对应 Boot 3 版本线。
第三方 AI 与向量 SDK 需要逐模块验证，不能通过覆盖几个根版本就认定运行兼容。
使用 JDK 17 编译，产物的目标字节码版本为 17。
上游的 Error Prone / NullAway 构建要求 JDK 17.0.19 及以上补丁版本，实际验证使用 Amazon Corretto 17.0.21。

## 构建

```bash
./mvnw clean package
```

上游构建会自动格式化源码。
只检查而不修改格式时使用 `-P'!format-apply,format-check'`。
兼容性 Enforcer 在默认生命周期中检查传递依赖，禁止 Boot 4、Spring 7、Jackson 3、Testcontainers 2、JUnit 6 以及下一代 Spring Data 产品版本。
各 Spring Data 产品的主版本不同，例如 Boot 3 使用的 MongoDB 模块是 4.x，Neo4j 模块是 7.x，不能统一按主版本 4 判断。

## 已采用的回迁方式

- Jackson 统一使用 `com.fasterxml.jackson`，补回日期模块，处理受检异常并保持工具参数、结构化输出和消息序列化的行为。
- Spring 7 Retry 切换为 Spring Retry，保留重试次数、异常分类、退避与 System One 的重试期限和 Retry-After 行为。
- Boot 自动配置、JDBC 初始化、Redis/Jedis、HTTP 客户端和测试依赖回迁到 Boot 3 版本线。
- MCP SDK 使用 Jackson 2 实现和 JSON Schema Validator 2.0.8（仍是 Jackson 2），排除其测试组件传递引入的 Testcontainers 2。
- `spring-ai-model` 保留旧 ZhiPu assistant 消息类型，供 Alibaba 图状态迁移使用；使用最新的有序消息 parts，不恢复旧 ZhiPu 模型客户端。
- Nullness 规则由 Spring Core 7.0.9 源码回迁，保留 JSpecify 与 Kotlin 字段、访问器、参数的实际可空性。
- Elasticsearch 与 OpenSearch 使用 Boot 3 对应的客户端和 API。

## 兼容边界

本分支跟随上游 2.1 API，不承诺与 Spring AI 1.1 在源码或行为上完全兼容。
消费项目需要处理上游 2.x 的 API 迁移，包括工具调用及已移除的模型集成。
MongoDB 旧版驱动无法在已经创建的 MongoClient 上追加框架元数据，本分支不执行上游新增的 `appendMetadata` 操作；向量功能保持原接口。
Maven groupId 保留为 `org.springframework.ai`，兼容版本不能与上游同坐标同版本混用。
当前为开发 SNAPSHOT；Maven 制品发布另行安排。
完整模块、单测、依赖与消费项目验证完成前，不应作为发布版使用。

## 验证范围

- 上游基准：`4cce4caa9653092624b20ae1113633b3ad97d256`。本分支版本为 `2.1.0-boot3-SNAPSHOT`，对应 Boot `3.5.15`，最低目标 JDK `17`，消费验证 JVM `17`。保留 `org.springframework.ai`、原 artifactId 和 Java 包名。
- 默认 reactor：Boot 2 为190模块，Boot 3/4为184模块。默认单元测试、依赖守卫和本地安装通过完整运行及后续按模块/测试类闭合；不冒充每次全量运行都一次通过。Boot 3/4库级全量测试使用JDK17，Java8分支用实际JVM8；实际业务消费另在JDK8/17/25验证。
- 三套冻结业务快照跑通启动、同步/SSE聊天、Token入库、图片生成下载、知识库解析/异步切片/带元数据过滤检索/删除。AI1另有各301项按最新类结果合并覆盖，包含隔离Redis恢复验证。外部模型和向量服务并非全部在线验证。
- 三套共同HTTP回归各10项通过，覆盖OpenAI、Azure Embedding、原生Gemini、通义Embedding/图片及应用ObservationRegistry、思考内容增量。相应服务层与工具类回归在实际JVM8/17/25分别51/54/61项通过。
- Tika整族分别为Boot2=2.9.4、Boot3=3.3.1、Boot4=4.0.0；BOM仅管理Tika自身组件，避免父BOM覆盖应用框架依赖。
- Java8分支不将Coherence、Boot3 devservices等受限可选模块计入默认支持；可选agent-utils源码兼容模块不包含旧Dream子系统。具体依赖、模块和API边界见本文件对应说明及compatibility目录。
- 消费项目需要适配Spring AI 2.x API。既有平台存储值、业务新增功能及Boot/JDK原生类型仍可不同，不能把“能够编译运行”理解为三个业务仓库源码完全相同。当前为开发兼容分支，后续扩展需补实际目标JVM验收。

## Redis public API migration

This older-Boot branch uses `redis.clients.jedis.JedisPooled` in the public builders for `RedisVectorStore`, `RedisChatMemoryRepository` / `RedisChatMemoryConfig`, and `DefaultSemanticCache` / `RedisVectorStoreHelper`. Upstream Spring AI 2.1 and the Boot 4 `main` branch use `RedisClient` in these positions. Applications constructing these low-level objects must supply the native branch's `JedisPooled`; an upstream `RedisClient` bean cannot be passed unchanged. High-level Spring AI interfaces remain the preferred integration boundary. This deliberate dependency adaptation is not a claim of identical low-level Redis APIs across branches.
