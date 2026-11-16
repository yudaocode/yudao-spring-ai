# Spring Web compatibility on Boot 2

RestClient and its supporting new API types are backported from official Spring Web 6.2.19 sources, retaining the Apache 2.0 headers and original authors. SOURCE.json records the exact source archive SHA256 and included files.
This module depends on Spring Web 5.3 and Java 8, and must never be added to Boot 3/4 applications. It does not replace existing Spring 5 classes.
Default request factory uses Boot 2's Apache HttpClient 4; default converters use Spring 5 RestTemplate's supported converter set. Newer Spring 6 optional converter implementations are not imported.
HTTP status predicates wrap raw numeric status codes, including nonstandard codes. Spring 5 response APIs and exception types are retained. The newer generic typed exception-body conversion API is unavailable.
RestClient observation remains active via Micrometer Observation; request attributes remain in RestClient's request context, while Spring 5 ClientHttpRequest has no getAttributes API.
The eight RestClientJava8Tests pass on real Java 8, covering JSON/URI/header exchange, standard and unknown status errors, custom status handling, interceptor reuse, builder isolation, response closure, and observation tags. Broader provider, streaming and consumer acceptance are still pending.
