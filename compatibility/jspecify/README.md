# JSpecify annotation compatibility for Java 8

Source: org.jspecify:jspecify:1.0.0 sources, Apache-2.0.
Only change: remove ElementType.MODULE from NullMarked target, since Java 8 lacks this enum constant and Spring 5 annotation scanning otherwise fails at runtime.
Keep all annotation names, retention and remaining targets intact.
This module replaces the original annotation jar on this branch; do not ship both.
