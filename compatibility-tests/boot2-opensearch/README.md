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
