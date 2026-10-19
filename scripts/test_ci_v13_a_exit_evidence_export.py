#!/usr/bin/env python3
"""Isolated regression checks: sanitize A-EXIT proofs and reject false greens."""
from pathlib import Path
from tempfile import TemporaryDirectory
import json
import unittest

from ci_v13_a_exit_evidence_export import (
    java_results, browser_results, acceptance_rows, problems, markdown,
)
from ci_v13_a_exit_ledger_gate import LEDGER


class EvidenceExportTest(unittest.TestCase):
    def test_java_aggregates_pass_and_failure_without_stdout_leak(self):
        with TemporaryDirectory() as d:
            path = Path(d) / "TEST-CI.xml"
            path.write_text('''<testsuite>
              <testcase classname="Safe" name="normal"/>
              <testcase classname="Safe" name="refused"><failure message="secret-should-not-leak"/></testcase>
              <system-out>secret-should-not-leak</system-out>
            </testsuite>''', encoding="utf-8")
            s = java_results(Path(d))
            self.assertEqual((s["total"], s["passed"], s["failed"]), (2, 1, 1))
            self.assertNotIn("secret-should-not-leak", str(s))

    def test_missing_browser_json_must_never_pass(self):
        with TemporaryDirectory() as d:
            s = browser_results(Path(d) / "missing.json")
            self.assertFalse(s["present"])
            self.assertEqual(s["total"], 0)

    def test_json_browser_counts_and_index_are_exact(self):
        with TemporaryDirectory() as d:
            path = Path(d) / "playwright.json"
            path.write_text(json.dumps({
                "stats": {"expected": 1, "unexpected": 0, "flaky": 0, "skipped": 0},
                "suites": [{
                    "specs": [{"title": "A-EXIT UI-29 malformed reply",
                               "tests": [{"status": "expected", "expectedStatus": "passed"}]}],
                    "suites": []
                }]
            }), encoding="utf-8")
            s = browser_results(path)
            self.assertEqual(s["total"], 1)
            self.assertEqual(s["tests"][0]["title"], "A-EXIT UI-29 malformed reply")

    def test_real_ledger_has_54_not_autosigned_entries(self):
        ledger = LEDGER.read_text(encoding="utf-8")
        rows, states = acceptance_rows(ledger, ["A-EXIT UI-29 malformed reply"])
        self.assertEqual(len(rows), 54)
        self.assertEqual(sum(item["id"] == "UI-29" for item in rows), 1)
        self.assertEqual(rows[28]["title_only_browser_candidates"],
                         ["A-EXIT UI-29 malformed reply"])
        self.assertEqual(states["cases"]["external_pending"], 3)

    def test_short_or_missing_suite_blocks_verification(self):
        record = {
            "java": {"total": 112, "passed": 112},
            "chromium": {"total": 149, "passed": 149, "tests": [
                {"status": "expected", "expected_status": "passed"} for _ in range(149)]},
            "provenance": {"github_sha": "a" * 40, "checkout_sha": "a" * 40},
            "acceptance": [{} for _ in range(54)],
        }
        self.assertTrue(any("Chromium" in x for x in problems(record)))
        record["chromium"]["total"] = 150
        record["chromium"]["passed"] = 150
        record["chromium"]["tests"].append({"status": "skipped", "expected_status": "passed"})
        self.assertTrue(any("non-passing" in x for x in problems(record)))

    def test_markdown_emphasizes_unsigned_scope(self):
        record = {
            "provenance": {"checkout_sha": "b" * 40, "pr_head_sha": None,
                           "run_url": "https://github.com/example/repo/actions/runs/1"},
            "java": {"passed": 112, "total": 112, "failed": 0, "error": 0, "skipped": 0},
            "chromium": {"passed": 150, "total": 150, "unexpected": 0,
                         "flaky": 0, "skipped": 0},
            "generated_at": "2026-10-19T00:00:00Z",
            "issues": [],
            "signoff_counts": {
                "pages": {"pending": 36, "external_pending": 0, "signed": 0},
                "cases": {"pending": 15, "external_pending": 3, "signed": 0},
            },
            "acceptance": [{"id": "UI-01", "name": "首页", "manual_status": "待人工签收",
                            "title_only_browser_candidates": []}],
        }
        m = markdown(record)
        self.assertIn("非人工签收", m)
        self.assertIn("36页面", m)
        self.assertIn("待人工签收", m)


if __name__ == "__main__":
    unittest.main()
