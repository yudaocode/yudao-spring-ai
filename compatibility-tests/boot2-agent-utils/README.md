# Boot 2 Agent Utils consumer

Build the matching Java 8 Spring AI distribution locally first.
With Maven/compiler on JDK 17, run `mvn -f compatibility-tests/boot2-agent-utils/pom.xml -Dcompat.test.jvm=/absolute/path/to/jdk8/bin/java test`.
The standalone application uses the native Boot 2.7.18 parent, the fork BOM, an explicit session dependency, and a real local HTTP model fixture.
It executes Dream's cross-session tool call, verifies principal isolation and unchanged session history, and persists Dream state.
No real provider or API key is used.
Boot-parent overrides align HTTP5/Core5, Logback, OkHttp and Kotlin with the tested runtime; see this POM for the exact properties.
