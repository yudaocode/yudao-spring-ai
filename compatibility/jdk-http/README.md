# Java 8 HTTP surface for MCP

MCP 2.2 uses JDK 11 HTTP and JDK 9 Flow types. This module supplies the subset used
by the source port, under `org.springframework.ai.compat.jdkhttp`. It runs on Spring
5 WebFlux and Apache HttpClient 5; it does not replace JDK packages or the Spring 5
HTTP implementation. It is not a general binary substitute for `java.net.http`.

Supported transport behavior includes asynchronous headers-first responses,
Reactive Streams demand/cancellation, request and connection timeout mapping,
proxy selection, TLS context/parameters, client-level HTTP version preferences,
redirect policy, explicit cookies and completion executor, and buffered string
responses. A per-request version differing from the client version is rejected.
MCP's default HTTP/1.1 transport is exercised with actual Java 8 tests.
The string handler uses UTF-8; MCP uses the publisher handler and its own decoding.
Authentication is provided by MCP request customizers and authorization handlers.
The JDK's Authenticator API and arbitrary JDK BodySubscriber implementations are
not part of this compatibility surface.

Clients own an Apache connection pool and must be closed. Legacy MCP transports
close their own client through closeGracefully. The modern HttpClientMcpTransport
implements AutoCloseable, like its Stdio counterpart, for explicit owner cleanup.
Standalone callers should use try-with-resources. A streaming body must be consumed
or cancelled before dropping its response. Resource handling does not drain an
unbounded SSE stream.

The fixed localhost test certificate and its public `test-only` password are test
fixtures only. Tests verify default TLS rejection and explicit trust/parameters.

Compatibility work is in progress. Production compilation, JVM execution, protocol
behavior, dependency/bytecode guards and complete consumer acceptance are distinct
checks; see the workspace validation notes for their current results.
