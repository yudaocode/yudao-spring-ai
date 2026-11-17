# AWS OpenSearch standalone starter consumer

This native Boot4.1.2 parent imports this branch's compatibility BOM and depends only on the AWS OpenSearch starter plus the test starter.
It supplies no AWS SDK dependencies itself, so missing credentials/region/signing dependencies cannot be masked by other AI modules.
Install the matching source distribution first, then run with the build VM on JDK17:

```sh
./mvnw -f compatibility-tests/aws-opensearch/pom.xml test
./mvnw -f compatibility-tests/aws-opensearch/pom.xml -Dcompat.test.jvm=/absolute/path/to/jdk25/bin/java test
```

The same installed artifacts passed both tests on native JDK17 and JDK25: real AWS transport bean creation with localhost/dummy credentials, and actual SigV4 signing with mocked HTTP execution.
No AWS account, cloud request or paid embedding model is involved.
The SDK version remains this branch's existing version; Java8 SDK backport changes are isolated to main-jdk8.
