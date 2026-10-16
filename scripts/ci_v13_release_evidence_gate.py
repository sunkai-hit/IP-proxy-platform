#!/usr/bin/env python3
"""V1.3 release-evidence guard: 31 signed-off candidate APIs and DOCX version drift.

A green result means the candidate interface register has not silently lost
rows and document mismatches are reported explicitly. It NEVER means remote
API contracts or real proxy delivery have been signed.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs/integration/V1.3-31接口联合技术签收矩阵-20261016.md"
DOC_DIR = ROOT / "docs/requirements"
EXPECTED = {
    *(f"A{i:02}" for i in range(1, 7)),
    *(f"B{i:02}" for i in range(1, 6)),
    *(f"C{i:02}" for i in range(1, 8)),
    *(f"D{i:02}" for i in range(1, 4)),
    *(f"E{i:02}" for i in range(1, 5)),
    *(f"F{i:02}" for i in range(1, 7)),
}
ROW = re.compile(r"^\\|\\s*([A-F][0-9]{2})\\s*\\|\\s*([^|]+)\\|\\s*\\`(GET|POST|PUT|PATCH|DELETE)\\`\\s*\\|\\s*\\`(/(?:product|business)/v1/[^\\` ]+)\\`\\s*\\|")

def version_markers(path: Path) -> set[str]:
    """Read OOXML text, including tables and the cover, without Word automation."""
    with ZipFile(path) as archive:
        root = ET.fromstring(archive.read("word/document.xml"))
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    text = " ".join((t.text or "") for t in root.findall(".//w:t", ns))
    return set(re.findall(r"V1\\.[0-9]+(?:-R[0-9]+)?", text))

def main() -> int:
    if not MATRIX.is_file():
        raise AssertionError("missing 31-item signoff matrix")
    lines = MATRIX.read_text(encoding="utf-8").splitlines()
    rows = []
    for line in lines:
        match = ROW.match(line)
        if match:
            rows.append((match.group(1), match.group(2).strip(), match.group(3), match.group(4)))
    actual = Counter(row[0] for row in rows)
    duplicates = sorted(key for key, count in actual.items() if count != 1)
    assert len(rows) == 31 and set(actual) == EXPECTED and not duplicates, (
        f"31-interface index mismatch: count={len(rows)} missing={sorted(EXPECTED-set(actual))} extra={sorted(set(actual)-EXPECTED)} duplicates={duplicates}")
    assert all(name for _, name, _, _ in rows)
    assert all(x.startswith(("/product/v1/", "/business/v1/")) for *_, x in rows)
    assert all("待双方签收" in line for line in lines if ROW.match(line)), (
        "a candidate row changed signoff state without a separate evidence review")
    print("PASS: 31 distinct candidate methods/paths from 66-page interface signoff index")
    for filename, expected in [
        ("IP代理管理平台需求说明书 V1.3.docx", "V1.3"),
        ("IP代理管理平台需求说明书 V1.3 概要版.docx", "V1.3"),
    ]:
        markers = version_markers(DOC_DIR / filename)
        if expected not in markers:
            print(f"REVIEW (non-blocking): {filename} content version markers={sorted(markers)}; filename and internal edition differ")
        else:
            print(f"PASS: {filename} contains {expected} edition marker")
    print("EVIDENCE GATE PASSED: this is a schema/index check, NOT contract signature or network acceptance")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("EVIDENCE GATE FAILED:", exc, file=sys.stderr)
        sys.exit(1)
