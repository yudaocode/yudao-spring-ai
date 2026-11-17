# AWS OpenSearch standalone starter consumer

This native Boot3.5.15 parent imports this branch's compatibility BOM and depends only on the AWS OpenSearch starter plus the test starter.
It supplies no AWS SDK dependencies itself, so missing credentials/region/signing dependencies cannot be masked by other AI modules.
Install the matching source distribution first, then run with the build VM on JDK17:

```sh
./mvnw -f compatibility-tests/aws-opensearch/pom.xml test
./mvnw -f compatibility-tests/aws-opensearch/pom.xml -Dcompat.test.jvm=/absolute/path/to/jdk25/bin/java test
```

The same installed artifacts passed both tests on native JDK17 and JDK25: real AWS transport bean creation with localhost/dummy credentials, and actual SigV4 signing with mocked HTTP execution.
No AWS account, cloud request or paid embedding model is involved.
The SDK version remains this branch's existing version; Java8 SDK backport changes are isolated to main-jdk8.

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
