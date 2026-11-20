# Docker Model Runner 真服务复验

此独立 Maven 工程通过当前分支的 `spring-ai-starter-model-openai` 自动配置启动最小 Boot 应用。同步用例检查正文、结束原因和 token 用量，SSE 用例检查多个事件、正文增量、结束原因和最后一帧的 token 用量。

启用 Docker Model Runner 的本机 TCP 访问并拉取模型。使用官方独立版 `dmr serve` 时，改用 `dmr pull` 拉取模型。[Docker Model Runner 文档](https://docs.docker.com/ai/model-runner/get-started/)

```bash
docker model pull ai/smollm2:135M-Q4_K_M
export DMR_BASE_URL=http://127.0.0.1:12434/engines/llama.cpp/v1
curl --fail "$DMR_BASE_URL/models"
```

如果 Runner 使用其他端口，调整 `DMR_BASE_URL`。用 JDK 17 启动 Maven，从当前分支的仓库根目录安装 starter 及其 reactor 依赖，再运行独立测试。将 `TEST_JAVA_HOME` 指向目标 JDK，按下表设置 `TEST_JAVA_SPEC`。

```bash
./mvnw -pl :spring-ai-bom,:spring-ai-starter-model-openai -am \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true install

./mvnw -f compatibility-tests/docker-model-runner/pom.xml clean test \
  -Dcompat.test.jvm="$TEST_JAVA_HOME/bin/java" \
  -Dcompat.java.version="$TEST_JAVA_SPEC" \
  -Ddmr.base-url="$DMR_BASE_URL" \
  -Ddmr.model=ai/smollm2:135M-Q4_K_M
```

| 分支 | Spring Boot | `TEST_JAVA_SPEC` |
| --- | --- | --- |
| `main-jdk8` | 2.7.18 | `1.8` |
| `main-jdk17` | 3.5.15 | `17` 或 `25` |
| `main` | 4.1.2 | `17` 或 `25` |

用例使用本地占位 API key，实际核对 Boot 和 JVM 版本。上游 `DockerModelRunnerWithOpenAiChatModelIT` 沿用原有停用配置。

## 工具回合和 JSON Schema 复验

在同一 Runner 导入一个支持工具调用的 Qwen3 0.6B GGUF。独立版 DMR 可以这样导入：

```bash
dmr package --gguf /path/to/qwen3.gguf --context-size 2048 ai/qwen3-compat:0.6b
```

保持文本模型可用，并给上面的 Maven 命令增加 `-Ddmr.tool-model=ai/qwen3-compat:0.6b`。工具用例读出模型返回的 `get_code` 调用，由 Spring AI 的 `ToolCallingManager` 执行 Java 工具，再把工具结果交回模型检查最终回答。结构化用例通过 `response_format=json_schema` 启用 strict 模式，检查字段名、字段类型和值。两个用例均使用非流式 Chat Completions。
