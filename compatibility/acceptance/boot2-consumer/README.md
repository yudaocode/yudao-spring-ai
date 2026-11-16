# Independent Boot 2 / Java 8 consumer gate

This is deliberately not a child reactor module: it imports the compatibility BOM and Boot 2.7.18 BOM as a consumer does, without inheriting the library build parent.
First install the selected library modules and spring-ai-bom from the branch using build JDK 17. Then run this standalone project with Maven itself on Java 8:

```sh
MAVEN_BASEDIR="$PWD/compatibility/acceptance/boot2-consumer" JAVA_HOME=/path/to/jdk8 /path/to/mvn -f compatibility/acceptance/boot2-consumer/pom.xml test
```

The Spring AI compatibility BOM must be imported before the Boot BOM when both are declared at the same level. It manages Jackson 2, Micrometer/Tracing, Kotlin, context propagation and Spring Retry 1.3.4. A consumer's dependency management can override transitive versions even if the library was built and tested with another version.
Four tests verify actual Java 8 execution, selected runtime versions, synchronous/streaming ChatClient calls, nullable/required tool schemas, YAML loading, Boot 2 Actuator startup and discovery/prototype scope of the RestClient compatibility auto-configuration.
This gate does not replace the three real business snapshots or all-provider/integration acceptance.


MAVEN_BASEDIR keeps the consumer independent of the checkout's JDK 17-only .mvn/jvm.config. The four default consumer tests are supplemented by two SSL bundle API/registry tests. RedisStackCompatibilityIT requires a separate disposable Redis Stack instance: set REDIS_STACK_TEST_PORT to its 127.0.0.1 mapped port and select it explicitly with -Dtest=Boot2ConsumerTests,SslBundleCompatibilityTests,RedisStackCompatibilityIT. Its two tests verify vector CRUD/KNN/metadata filtering, JSON chat memory, native Jedis coexistence, semantic-cache response/TTL/context isolation/clear. They use unique key and index prefixes and deterministic local embeddings; use a disposable instance. No external model credentials are required.
