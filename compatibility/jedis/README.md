# Redis compatibility with the Boot 2 host

Boot 2 / Spring Data Redis 2.7 retains native Jedis 3.8.0, including BinaryJedis and JedisShardInfo. Replacing that host SDK with Jedis 6 would break its connection factory.

The companion spring-ai-jedis-runtime-compat artifact instead isolates Jedis 6.0.0 and its required pool/JSON/auth dependencies during generate-resources. They are not exposed as transitive Jedis 6 dependencies. Build tooling verifies the pinned runtime-jar hashes and Java 8 base-class bytecode, preserves licenses and records generated files so it only replaces its own previous output.

This artifact exposes a JedisPooled facade and retains the new search/json packages, which do not exist in the host SDK. HostAndPort and JedisClientConfig are adapted from native 3.8, including database, authentication, SSL, host mapping and timeouts. Internal pipeline types are isolated. This is the Spring AI API subset, not a complete Jedis 6 binary mirror; code importing every Jedis implementation type is not guaranteed to be source-compatible.

JedisHostCompatibilityIT runs on Java 8 with a temporary authenticated Redis server and verifies both clients, pipeline and database isolation. The independent acceptance/boot2-consumer gate additionally verifies Redis Stack vector CRUD/filtering, JSON chat memory and semantic-cache expiry/context isolation. See the validation ledger for the exact completed runs; neither test requires a paid model API.
