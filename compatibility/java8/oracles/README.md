# SchemaNames cross-JVM oracle

Run SchemaNamesOracle.java on JDK 17 with the official org.neo4j:neo4j-cypher-dsl-schema-name-support:2024.5.1 jar on the classpath, passing schema-names-inputs.txt and the output TSV path. The library module test compares the Java 8 source port with all 434 official results, including empty/null names, backticks, Unicode escapes, punctuation and version-specific escaping. The oracle is not part of the Java 8 runtime.
