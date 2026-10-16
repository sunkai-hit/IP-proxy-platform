#!/usr/bin/env python3
"""V1.3 cross-artifact baseline *static* gate; no external network or packages.

Verifies the three uploaded Word files really exist, are readable DOCX, and
the detailed spec contains the agreed 36 page specs and 18 acceptance cases.
It checks file-level engineering coverage and two high-risk protocol rules.
This gate does NOT certify UX correctness or genuine proxy/network delivery.
"""
from __future__ import annotations
from pathlib import Path
from zipfile import ZipFile
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DOC_DIR = ROOT / "docs/requirements"
DOCS = {
    "detailed": "IP代理管理平台需求说明书 V1.3.docx",
    "overview": "IP代理管理平台需求说明书 V1.3 概要版.docx",
    "interface": "《IP代理管理平台接口说明书 V1.0》.docx",
}
# Maps the 36 V1.3 specifications to existing Vue module-level evidence.
# A matching file is an entry point, NOT proof that all fields/interactions pass.
PAGE_VIEWS = {
  1: "frontend/src/views/DashboardView.vue",
  2: "frontend/src/views/customer/CustomerListView.vue",
  3: "frontend/src/views/customer/CustomerDetailView.vue",
  4: "frontend/src/views/customer/CustomerAuthView.vue",
  5: "frontend/src/views/customer/CustomerAccountView.vue",
  6: "frontend/src/views/resource/ResourceOverviewView.vue",
  7: "frontend/src/views/resource/V12ResourceListView.vue",
  8: "frontend/src/views/resource/V12ResourceListView.vue",
  9: "frontend/src/views/resource/V12ResourceDetailView.vue",
 10: "frontend/src/views/resource/ResourcePoolView.vue",
 11: "frontend/src/views/resource/ExclusiveResourceView.vue",
 12: "frontend/src/views/resource/V12ResourceDetailView.vue",
 13: "frontend/src/views/service/ServiceDetailView.vue",
 14: "frontend/src/views/resource/ResourceDomainView.vue",
 15: "frontend/src/views/resource/ResourceSupplierView.vue",
 16: "frontend/src/views/resource/SupplierPurchaseView.vue",
 17: "frontend/src/views/resource/ReverseProxyView.vue",
 18: "frontend/src/views/product/ProductListView.vue",
 19: "frontend/src/views/product/PackageView.vue",
 20: "frontend/src/views/service/OrderListView.vue",
 21: "frontend/src/views/service/ServiceProvisionView.vue",
 22: "frontend/src/views/service/ServiceProvisionView.vue",
 23: "frontend/src/views/service/ServiceDetailView.vue",
 24: "frontend/src/views/service/ServiceDetailView.vue",
 25: "frontend/src/views/service/ServiceChangeView.vue",
 26: "frontend/src/views/service/ServiceReleaseView.vue",
 27: "frontend/src/views/runtime/MonitorView.vue",
 28: "frontend/src/views/runtime/MonitorView.vue",
 29: "frontend/src/views/runtime/RuntimeLogView.vue",
 30: "frontend/src/views/longmonitor/LongMonitorListView.vue",
 31: "frontend/src/views/longmonitor/BotListView.vue",
 32: "frontend/src/views/longmonitor/LongAlarmView.vue",
 33: "frontend/src/views/runtime/StatisticsView.vue",
 34: "frontend/src/views/SystemManagementView.vue",
 35: "frontend/src/views/SystemManagementView.vue",
 36: "frontend/src/layouts/ConsoleLayout.vue",
}

def check(test: bool, description: str) -> None:
    if not test:
        raise AssertionError(description)
    print("PASS", description)

def paragraphs(docx: Path) -> list[str]:
    with ZipFile(docx) as archive:
        bad = archive.testzip()
        check(bad is None, f"{docx.name}: intact DOCX")
        root = ET.fromstring(archive.read("word/document.xml"))
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    return ["".join(t.text or "" for t in p.findall(".//w:t", ns))
            for p in root.findall(".//w:p", ns)]

def main() -> int:
    contents: dict[str, list[str]] = {}
    for purpose, filename in DOCS.items():
        path = DOC_DIR / filename
        check(path.is_file() and path.stat().st_size > 100_000, f"{purpose}: document uploaded")
        contents[purpose] = paragraphs(path)

    detailed = contents["detailed"]
    page_numbers = set()
    for text in detailed:
        match = re.match(r"^10\.\d+\.(\d{2})\s", text.strip())
        if match:
            page_numbers.add(int(match.group(1)))
    check(page_numbers == set(range(1, 37)),
          f"V1.3 detailed spec: 36 pages present (found {sorted(page_numbers)})")
    case_numbers = set()
    for text in detailed:
        match = re.match(r"^验收案例\s*(\d{1,2})\b", text.strip())
        if match:
            case_numbers.add(int(match.group(1)))
    check(case_numbers == set(range(1, 19)),
          f"V1.3 detailed spec: 18 E2E cases present (found {sorted(case_numbers)})")

    check(set(PAGE_VIEWS) == set(range(1, 37)), "36 page-to-module mappings complete")
    for i, view in PAGE_VIEWS.items():
        check((ROOT / view).is_file(), f"page {i:02d}: mapped Vue entry exists")
    check("V1.3" in (ROOT / "frontend/src/layouts/ConsoleLayout.vue").read_text(),
          "formal frontend displays V1.3")
    check("V1.3" in (ROOT / "prototype/index.html").read_text(),
          "mock prototype displays V1.3")
    check("V1.3" in (ROOT / "docs/requirements/README.md").read_text(),
          "latest requirements index selects V1.3")
    check("V1.3" in (ROOT / "README.md").read_text(),
          "repository entry selects V1.3")
    migrations = ROOT / "database/migration"
    versions = {
        int(match.group(1)) for file in migrations.glob("V*__*.sql")
        if (match := re.match(r"V(\d+)__", file.name))
    }
    check(set(range(1, 28)).issubset(versions), "historical Flyway V1-V27 present")
    wire = (ROOT / "backend/src/main/java/com/ipproxy/platform/resource/v12/ProductGroupStopClient.java").read_text()
    for hdr in ('X-Request-Id', 'Idempotency-Key', 'X-Operation-Id'):
        check(f'.header("{hdr}",opId)' in wire, f"product write preserves {hdr}")
    config = (ROOT / "backend/src/main/resources/application.yml").read_text()
    check("PRODUCT_GROUP_WRITES_ENABLED:false" in config,
          "product network writes still disabled by default")
    check((ROOT / "scripts/ci_r3_release_postgres_smoke.sh").is_file(),
          "database-backed release regression retained")
    print("BASELINE GATE PASSED: file/structure checks ONLY; real-device verification remains pending")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("BASELINE GATE FAILED:", exc, file=sys.stderr)
        sys.exit(1)
