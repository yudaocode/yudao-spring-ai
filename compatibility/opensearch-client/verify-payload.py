#!/usr/bin/env python3
"""Verify the original SDK payload outside the two recompiled source families."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

module = Path(__file__).resolve().parent
receipt = json.loads((module / "src/main/resources/META-INF/spring-ai-compatibility/opensearch-source.json").read_text())
upstream = Path(sys.argv[1])
compat = Path(sys.argv[2])
assert hashlib.sha256(upstream.read_bytes()).hexdigest() == receipt["binarySha256"]
families = [row["path"][:-5] for row in receipt["recompiledSources"]]

def recompiled(name):
    return any(name == family + ".class" or name.startswith(family + "$") for family in families)

with zipfile.ZipFile(upstream) as original, zipfile.ZipFile(compat) as rebuilt:
    expected = set(original.namelist())
    actual = set(rebuilt.namelist())
    checked = 0
    for name in sorted(expected):
        if name.endswith("/") or name == "META-INF/MANIFEST.MF" or name.startswith("META-INF/versions/") or name == "module-info.class" or recompiled(name):
            continue
        assert name in actual, "Missing SDK payload entry: " + name
        assert original.read(name) == rebuilt.read(name), "Changed SDK payload entry: " + name
        checked += 1
    for family in families:
        assert family + ".class" in actual, "Missing recompiled class: " + family
    original_base_classes = {n for n in expected if n.endswith(".class") and not n.startswith("META-INF/versions/") and n != "module-info.class"}
    actual_classes = {n for n in actual if n.endswith(".class")}
    assert all(recompiled(n) for n in original_base_classes - actual_classes), "Unexpected missing SDK classes"
    assert all(recompiled(n) for n in actual_classes - original_base_classes), "Unexpected added SDK classes"
    print(json.dumps({"upstream": receipt["upstream"], "unchangedEntries": checked,
                      "upstreamBaseClasses": len(original_base_classes), "compatClasses": len(actual_classes),
                      "recompiledSourceFamilies": families, "verified": True}, indent=2))
