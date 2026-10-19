#!/usr/bin/env python3
"""Sanitized automated A-EXIT evidence. No actual human/network signoff."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from ci_v13_a_exit_ledger_gate import (
    LEDGER, section, numbered_rows, exact_ids, validate_acceptance_entries,
)

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "a-exit-ci-evidence"
JAVA_DIR = ROOT / "backend/target/surefire-reports"
BROWSER_FILE = ROOT / "a-exit-playwright.json"


def checkout_sha():
    value = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
                           text=True, check=True).stdout.strip()
    assert re.fullmatch("[0-9a-f]{40}", value), "No valid checked-out Git commit"
    return value


def safe(value):
    return re.sub(r"\s+", " ", str(value)).replace("|", " / ")[:240].strip()


def java_results(path):
    counts = Counter()
    names = []
    files = sorted(path.glob("TEST-*.xml"))
    for f in files:
        root = ET.parse(f).getroot()
        for c in root.iter("testcase"):
            status = ("failed" if c.find("failure") is not None else
                      "error" if c.find("error") is not None else
                      "skipped" if c.find("skipped") is not None else "passed")
            counts[status] += 1
            names.append({"class": safe(c.get("classname", "")),
                          "test": safe(c.get("name", "")), "result": status})
    return {"reports": len(files), "total": sum(counts.values()),
            "passed": counts["passed"], "failed": counts["failed"],
            "error": counts["error"], "skipped": counts["skipped"], "tests": names}


def browser_results(path):
    if not path.is_file():
        return {"present": False, "total": 0, "passed": 0, "unexpected": 0,
                "flaky": 0, "skipped": 0, "tests": []}
    raw = json.loads(path.read_text(encoding="utf-8"))
    stats = raw["stats"]
    keys = ("expected", "unexpected", "flaky", "skipped")
    assert all(type(stats.get(k)) is int and stats[k] >= 0 for k in keys), "Invalid browser test counts"
    tests = []

    def walk(suites):
        assert isinstance(suites, list), "Invalid Playwright suites"
        for suite in suites:
            assert isinstance(suite, dict), "Invalid Playwright suite"
            for spec in suite.get("specs", []):
                for t in spec.get("tests", []):
                    tests.append({"title": safe(spec.get("title", "")),
                                  "status": safe(t.get("status", "UNKNOWN")),
                                  "expected_status": safe(t.get("expectedStatus", "UNKNOWN"))})
            walk(suite.get("suites", []))

    walk(raw["suites"])
    return {"present": True, "total": sum(stats[k] for k in keys),
            "passed": stats["expected"], "unexpected": stats["unexpected"],
            "flaky": stats["flaky"], "skipped": stats["skipped"], "tests": tests}


def acceptance_rows(ledger, titles):
    pages = numbered_rows(section(ledger, "## 二、详细需求第10章", "## 三、详细需求第12章"))
    cases = numbered_rows(section(ledger, "## 三、详细需求第12章", "## 四、数据库和跨系统"))
    exact_ids(pages, 36, "UI")
    exact_ids(cases, 18, "CASE")
    counts = {
        "pages": validate_acceptance_entries(pages, "UI"),
        "cases": validate_acceptance_entries(cases, "CASE", frozenset({"07", "10", "11"})),
    }
    candidates = {}
    for title in titles:
        for number in re.findall(r"\bUI-(\d{2})\b", title):
            if number in pages:
                candidates.setdefault(number, set()).add(title)
    items = []
    for kind, source in (("UI", pages), ("CASE", cases)):
        for number, row in sorted(source.items()):
            cells = [v.strip() for v in row.strip().strip("|").split("|")]
            items.append({"id": kind + "-" + number, "name": safe(cells[1]),
                          "manual_status": cells[7],
                          "archive_present": cells[6] != "未归档",
                          "title_only_browser_candidates":
                              sorted(candidates.get(number, set())) if kind == "UI" else []})
    return items, counts


def provenance():
    sha = checkout_sha()
    event_file = os.environ.get("GITHUB_EVENT_PATH", "")
    pr_head = None
    if event_file and Path(event_file).is_file():
        event = json.loads(Path(event_file).read_text(encoding="utf-8"))
        pr_head = (event.get("pull_request") or {}).get("head", {}).get("sha")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run = os.environ.get("GITHUB_RUN_ID", "")
    url = "https://github.com/" + repo + "/actions/runs/" + run if (
        re.fullmatch(r"[\w.-]+/[\w.-]+", repo) and run.isdigit()) else None
    return {"checkout_sha": sha, "github_sha": os.environ.get("GITHUB_SHA"),
            "pr_head_sha": pr_head, "trigger": os.environ.get("GITHUB_EVENT_NAME", "LOCAL"),
            "run_id": run or None, "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
            "run_url": url, "scope": "backend-smoke only; check full CI separately"}


def problems(data, min_java=112, min_browser=150):
    j, b, p = data["java"], data["chromium"], data["provenance"]
    issues = []
    if j["total"] < min_java or j["passed"] != j["total"]:
        issues.append("Java JUnit missing or not all passing")
    if b["total"] < min_browser or b["passed"] != b["total"]:
        issues.append("Chromium JSON missing or not all passing")
    if b["total"] != len(b["tests"]):
        issues.append("Chromium test index and summary counts inconsistent")
    if any(t["status"] != "expected" or t["expected_status"] != "passed" for t in b["tests"]):
        issues.append("Chromium contains non-passing, skipped or expected-failure result")
    if p["github_sha"] and p["checkout_sha"] != p["github_sha"]:
        issues.append("Checkout SHA differs from GitHub run SHA")
    if len(data["acceptance"]) != 54:
        issues.append("Acceptance register missing 54 entries")
    return issues


def markdown(data):
    j, b, p = data["java"], data["chromium"], data["provenance"]
    state = "完整的自动化索引（仍非人工签收）" if not data["issues"] else "不完整，不能用于通过判定"
    lines = [
        "# V1.3 A-EXIT 自动化测试证据索引（非人工签收）", "",
        "此文件来自 backend-smoke 的实际 JUnit XML 和 Chromium JSON 报告。只提取测试名称及汇总，不包含原始请求、密码、日志、截图或客户数据。",
        "明确限制：本文件不证明前端/Docker 两项作业通过，不证明任何36页/18案例的业务人工签署，也不证明真实产品联网交付。", "",
        "状态：" + state,
        "检出提交 SHA：" + p["checkout_sha"],
        "PR Head SHA（如有）：" + str(p["pr_head_sha"] or "无"),
        "CI 运行链接：" + str(p["run_url"] or "LOCAL"),
        "Java：" + str(j["passed"]) + "/" + str(j["total"]) + "，失败/错误/跳过：" +
            "/".join(str(j[k]) for k in ("failed", "error", "skipped")),
        "Chromium：" + str(b["passed"]) + "/" + str(b["total"]) + "，意外/不稳定/跳过：" +
            "/".join(str(b[k]) for k in ("unexpected", "flaky", "skipped")),
        "生成时间（UTC）：" + data["generated_at"], "",
    ]
    if data["issues"]:
        lines += ["## 未通过完整性校验", ""] + ["- " + s for s in data["issues"]] + [""]
    lines += ["## 唯一台账签署状态（仅转录已有声明）", "",
              "| 类型 | 待人工 | 待外部 | 已标注已签（真实性未知） |",
              "|---|---:|---:|---:|"]
    for key, label in (("pages", "36页面"), ("cases", "18案例")):
        d = data["signoff_counts"][key]
        lines.append("| " + label + " | " + " | ".join(
            str(d[x]) for x in ("pending", "external_pending", "signed")) + " |")
    lines += ["", "## Chromium 测试标题与 UI 编号的候选匹配", "",
              "只匹配测试标题明确出现的 UI-xx 编号；不代表字段、按钮或整个页面已获覆盖，零匹配也不代表无任何测试。",
              "", "| 编号与页面 | 人工状态 | 标题匹配数 |", "|---|---|---:|"]
    for r in data["acceptance"]:
        if r["id"].startswith("UI-"):
            lines.append("| " + r["id"] + " " + r["name"] + " | " +
                         r["manual_status"] + " | " + str(len(r["title_only_browser_candidates"])) + " |")
    lines += ["", "详尽测试名称和54项记录见同包的 manifest.json。人工执行人、复核人和证据必须回填到唯一台账。", ""]
    return "\n".join(lines)


def generate(path):
    path.mkdir(parents=True, exist_ok=True)
    j = java_results(JAVA_DIR)
    b = browser_results(BROWSER_FILE)
    rows, counts = acceptance_rows(LEDGER.read_text(encoding="utf-8"),
                                   [r["title"] for r in b["tests"]])
    data = {"schema_version": 1, "level": "AUTOMATED_INDEX_NOT_SIGNOFF",
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "provenance": provenance(), "java": j, "chromium": b,
            "acceptance": rows, "signoff_counts": counts}
    data["issues"] = problems(data)
    (path / "manifest.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (path / "README.md").write_text(markdown(data), encoding="utf-8")
    return data


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--verify", action="store_true")
    p.add_argument("--out", type=Path, default=OUTPUT)
    p.add_argument("--min-java", type=int, default=112)
    p.add_argument("--min-browser", type=int, default=150)
    a = p.parse_args()
    if a.verify:
        data = json.loads((a.out / "manifest.json").read_text(encoding="utf-8"))
        issues = problems(data, a.min_java, a.min_browser)
        if data["provenance"]["checkout_sha"] != checkout_sha():
            issues.append("Manifest SHA differs from current checkout")
        if issues:
            print("A-EXIT EVIDENCE INCOMPLETE:", "; ".join(issues), file=sys.stderr)
            return 1
        print("A-EXIT INDEX VERIFIED", data["provenance"]["checkout_sha"],
              "Java", data["java"]["passed"], "Chromium", data["chromium"]["passed"],
              "54 IDs; HUMAN SIGNOFF UNVERIFIED")
        return 0
    data = generate(a.out)
    print("A-EXIT sanitized record exported; complete=", not bool(data["issues"]))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("A-EXIT EXPORT ERROR:", type(e).__name__, e, file=sys.stderr)
        sys.exit(1)
