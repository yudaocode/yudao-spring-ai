# Boot 2 Micrometer Observation support

This source port provides the Observation registry/customizer/predicate/filter/handler grouping from Boot 3.5.15 on native Boot 2 / Spring 5 and the compatibility BOM Micrometer versions. Original authors and Apache-2.0 headers are preserved; source hashes are packaged with the artifact. It imports no Boot 3 actuator runtime. AI starters include this shared autoconfiguration.

The port does not supply the Boot 3 HTTP server observation filters or a full tracing setup. Application-provided ObservationRegistry beans are retained and customized through the original postprocessor. Model token handlers register with the same registry rather than silently using NOOP.
