#!/usr/bin/env python3
"""A-EXIT static evidence integrity gate, NOT a human or network signoff.

The unique 36-page/18-case ledger must remain complete. 11 runtime log
categories and 7 statistics categories must retain actual frontend entries.
Passing this gate means only that the signed-off scope did not silently shrink.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/project-progress/V1.3-36页面与18案例-全工程收敛核对表-20261015.md"
LOGS = ROOT / "frontend/src/views/runtime/RuntimeLogView.vue"
STATS = ROOT / "frontend/src/views/runtime/StatisticsView.vue"
E2E = ROOT / "frontend/tests/e2e/v13-real-backend.spec.ts"
CI = ROOT / ".github/workflows/v12-ci.yml"


def section(source: str, beginning: str, ending: str) -> str:
    assert beginning in source and ending in source, f"missing ledger section {beginning!r}"
    return source.split(beginning, 1)[1].split(ending, 1)[0]


def numbered_rows(source: str) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in source.splitlines():
        m = re.match(r"^\|\s*(\d{2})\s*\|\s*([^|]+)\|", line)
        if not m:
            continue
        key, title = m.groups()
        assert key not in rows, f"duplicate acceptance ID {key}"
        assert title.strip(), f"empty title for {key}"
        rows[key] = line
    return rows


def exact_ids(rows: dict[str, str], quantity: int, label: str) -> None:
    expected = {f"{i:02}" for i in range(1, quantity + 1)}
    assert set(rows) == expected, (
        f"{label} count/IDs drifted: missing={sorted(expected-set(rows))} "
        f"unexpected={sorted(set(rows)-expected)}"
    )


def validate_acceptance_entries(rows: dict[str, str], label: str,
                                external_ids: frozenset[str] = frozenset()) -> dict[str, int]:
    """Verify all 8 cells and that a claimed human signoff has real evidence metadata.

    This is FORMAT validation only, never authentication of people or artifacts.
    """
    outcomes = {"pending": 0, "external_pending": 0, "signed": 0}
    for identifier, row in rows.items():
        cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
        assert len(cells) == 8, f"{label} {identifier}: expected 8 table columns, got {len(cells)}"
        assert len(cells[4]) >= 18, f"{label} {identifier}: missing actionable test and failure path"
        assert len(cells[5]) >= 15, f"{label} {identifier}: missing DB/audit/screenshot evidence requirement"
        actual, status = cells[6], cells[7]
        allowed = ("待人工签收", "待外部签收（非A）", "已人工签收", "已外部签收")
        assert status in allowed, f"{label} {identifier}: unexpected signoff state {status!r}"
        if identifier in external_ids:
            assert status in ("待外部签收（非A）", "已外部签收"), (
                f"{label} {identifier}: external network/gateway case cannot be signed as A-stage"
            )
        if status in ("待人工签收", "待外部签收（非A）"):
            if status == "待人工签收":
                outcomes["pending"] += 1
            else:
                outcomes["external_pending"] += 1
            continue
        assert actual != "未归档", f"{label} {identifier}: signoff without archived evidence"
        assert re.search(r"\[[^\]]+\]\((?:https?://|\.{1,2}/)[^)]+\)", actual), (
            f"{label} {identifier}: claimed signoff has no accessible evidence link"
        )
        for required in ("执行人：", "复核人：", "日期："):
            assert required in actual, f"{label} {identifier}: signed item missing {required}"
        assert re.search(r"日期：20\d{2}-\d{2}-\d{2}", actual), (
            f"{label} {identifier}: signed item needs a date in YYYY-MM-DD"
        )
        person = re.search(r"执行人：([^；; ]+)", actual)
        reviewer = re.search(r"复核人：([^；; ]+)", actual)
        assert person and reviewer and person.group(1) != reviewer.group(1), (
            f"{label} {identifier}: two distinct human executors/reviewers required"
        )
        outcomes["signed"] += 1
    return outcomes


def main() -> int:
    ledger = LEDGER.read_text(encoding="utf-8")
    pages = numbered_rows(section(ledger, "## 二、详细需求第10章", "## 三、详细需求第12章"))
    cases = numbered_rows(section(ledger, "## 三、详细需求第12章", "## 四、数据库和跨系统"))
    exact_ids(pages, 36, "V1.3 pages")
    exact_ids(cases, 18, "V1.3 acceptance cases")
    page_section = section(ledger, "## 二、详细需求第10章", "## 三、详细需求第12章")
    case_section = section(ledger, "## 三、详细需求第12章", "## 四、数据库和跨系统")
    assert "人工离线操作与反例 | 应留证据 | 已归档证据" in page_section
    assert "离线操作／外部阻塞 | 应留证据 | 已归档证据" in case_section
    pages_receipts = validate_acceptance_entries(pages, "UI")
    cases_receipts = validate_acceptance_entries(
        cases, "CASE", frozenset({"07", "10", "11"}))
    # Manual-signature status is a separate evidence item; do not infer it
    # from free-text row wording (e.g. "不得..." or "仍阻断").
    assert "尚未完成字段、权限、业务动作" in ledger
    assert "不可把这三者合并" in ledger
    logs = LOGS.read_text(encoding="utf-8")
    statistics = STATS.read_text(encoding="utf-8")
    log_names = re.findall(r"\{name:'([^']+)',label:'\d{2} ", logs)
    stat_names = re.findall(r"\{key:'([^']+)',label:'[^']+'", statistics)
    assert len(log_names) == len(set(log_names)) == 11, f"11 log tabs drifted: {log_names}"
    assert len(stat_names) == len(set(stat_names)) == 7, f"7 stats tabs drifted: {stat_names}"
    for required in ("operation", "extract", "usage", "api"):
        assert required in log_names
    assert set(stat_names) == {"centos", "ros", "lines", "pools", "customers", "products", "suppliers"}
    e2e = E2E.read_text(encoding="utf-8")
    for marker in ("A-EXIT UI-29 log HTTP200", "A-EXIT UI-29 extended operation log",
                   "A-EXIT UI-33 invalid HTTP200"):
        assert marker in e2e, f"missing Chromium read-model regression: {marker}"
    workflow = CI.read_text(encoding="utf-8")
    assert "scripts/ci_v13_a_exit_ledger_gate.py" in workflow
    assert "scripts/ci_v13_a_exit_evidence_export.py" in workflow, (
        "A-EXIT test evidence export must remain wired to CI")
    assert "actions/upload-artifact@v4" in workflow, (
        "Sanitized evidence manifest must be downloadable from its exact CI run")
    assert "tests/e2e/v13-real-backend.spec.ts" in workflow
    print("A-EXIT STRUCTURE PASS: 36 distinct pages + 18 cases all have actionable steps,"
          " required evidence, archive and explicit signoff state;"
          " 11 log + 7 statistics categories unchanged")
    print(f"A-EXIT SIGNOFF COUNTS (self-reported, NOT validated signatures): "
          f"page={pages_receipts}; cases={cases_receipts}")
    print("EVIDENCE LEVEL: formatting/coverage only. Signed labels, if any, "
          "require a link and two named reviewers; CI does not verify evidence authenticity, "
          "human actions, or actual network delivery.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"A-EXIT STRUCTURE FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
