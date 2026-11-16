# ITU Java 8 API compatibility

The main Java sources and leap-second resource come from `com.ethlo.time:itu:1.17.0:sources`.
The source archive SHA-256 is `288e10af13dc1847fee1298cc8fbb90af6a7c24c5afecd4a86ea657e4e876b59`.
Original packages, copyright and Apache 2.0 headers are retained.
The original sources are compiled unchanged using `javac --release 8`, instead of relying on source/target alone.
This makes overload resolution bind `Math.multiplyExact(long, long)`; original 1.14.0 and latest 1.17.0 binaries are major52 but reference the Java9-only `(long, int)` overload, causing `NoSuchMethodError` when parsing durations on Java8.
The JSON-schema dependency excludes the original ITU jar and consumes this separate compatibility coordinate; no artifact is published under the original group.
The same leap-second CSV is included at the original resource path.
Native8 regression tests cover duration parsing, overflow and date/time behavior; the complete upstream ITU test suite is not included.
