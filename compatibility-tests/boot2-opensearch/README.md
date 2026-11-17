# Native Boot2 OpenSearch consumer

Install the matching Java8 Spring AI source distribution first.
With the Maven/compiler VM on JDK17, run:

```sh
./mvnw -f compatibility-tests/boot2-opensearch/pom.xml -Dcompat.test.jvm=/absolute/path/to/jdk8/bin/java test
```

The fixture uses the AWS OpenSearch starter alone, with the native Boot2.7.18 parent and fork BOM.
Five native8 cases verify both ordinary exact/approximate HTTP vector queries, the AWS auto-configuration's real client bean and an actual SDK SigV4 signing path with a mocked HTTP client, plus an optional JSON-B mapper round trip.
Ordinary starter acceptance was also run separately before the AWS-only check.
The runtime contains exactly one installed SDK implementation; provided original SDK classes cannot mask an incomplete compatibility jar.
The POM explicitly aligns HTTP5/Core5 and JSON-P/JSON-B/Yasson namespace versions inherited from the Boot2 parent.
Embedding vectors and HTTP responses are deterministic local fixtures, not real model/server output.
AWS credentials are dummy strings, the client bean uses localhost, and the signer test replaces HTTP execution with a mock; no cloud request is sent.
See the SDK module's payload verifier and static JDK API test for bytecode-level coverage.
