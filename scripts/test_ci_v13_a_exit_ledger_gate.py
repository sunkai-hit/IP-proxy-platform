#!/usr/bin/env python3
"""Regression tests proving the A-EXIT ledger gate refuses fabricated signoffs."""
import unittest

from ci_v13_a_exit_ledger_gate import validate_acceptance_entries


class HumanEvidenceGateTest(unittest.TestCase):
    def entry(self, archive="未归档", status="待人工签收",
              action="管理员：核对主子表并注入查询失败后重试，不得冒称成功",
              needed="页面截图、真实PostgreSQL关系及审计Request ID"):
        return {
            "01": "| " + " | ".join([
                "01", "首页工作台", "DashboardView.vue", "尚需人工验收",
                action, needed, archive, status
            ]) + " |"
        }

    def test_unsigned_row_is_allowed_not_counted_as_signed(self):
        self.assertEqual(
            validate_acceptance_entries(self.entry(), "UI"),
            {"pending": 1, "external_pending": 0, "signed": 0}
        )

    def test_all_eight_columns_must_exist(self):
        sample = self.entry()
        sample["01"] = sample["01"].replace(" | 未归档", "")
        with self.assertRaisesRegex(AssertionError, "8 table columns"):
            validate_acceptance_entries(sample, "UI")

    def test_operational_steps_and_proof_cannot_be_empty(self):
        with self.assertRaisesRegex(AssertionError, "actionable"):
            validate_acceptance_entries(self.entry(action="查看"), "UI")
        with self.assertRaisesRegex(AssertionError, "evidence"):
            validate_acceptance_entries(self.entry(needed="截图"), "UI")

    def test_claiming_pass_without_artifact_is_blocked(self):
        with self.assertRaisesRegex(AssertionError, "without archived evidence"):
            validate_acceptance_entries(self.entry(status="已人工签收"), "UI")
        with self.assertRaisesRegex(AssertionError, "evidence link"):
            validate_acceptance_entries(
                self.entry(status="已人工签收", archive="执行人：张三；复核人：李四；日期：2026-10-19"),
                "UI"
            )

    def test_same_person_cannot_self_review(self):
        archive="[截图和审计](https://example.org/proof);执行人：张三；复核人：张三；日期：2026-10-19"
        with self.assertRaisesRegex(AssertionError, "distinct"):
            validate_acceptance_entries(self.entry(archive=archive, status="已人工签收"), "UI")

    def test_signed_requires_two_people_and_date(self):
        archive="[截图和审计](https://example.org/proof);执行人：张三；复核人：李四；日期：2026-10-19"
        self.assertEqual(
            validate_acceptance_entries(self.entry(archive=archive, status="已人工签收"), "UI")["signed"],
            1
        )

    def test_external_network_case_cannot_be_labeled_a_stage_acceptance(self):
        archive="[日志](https://example.org/proof);执行人：张三；复核人：李四；日期：2026-10-19"
        with self.assertRaisesRegex(AssertionError, "external network"):
            validate_acceptance_entries(self.entry(archive=archive, status="已人工签收"), "CASE",
                                        frozenset({"01"}))
        self.assertEqual(
            validate_acceptance_entries(self.entry(status="待外部签收（非A）"), "CASE",
                                        frozenset({"01"}))["external_pending"], 1
        )


if __name__ == "__main__":
    unittest.main()
