#!/usr/bin/env python3
"""SDK-free contracts for Issue #8 conflict warning flows."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/flowpilot/app"
TEST = ROOT / "app/src/androidTest/java/com/flowpilot/app/ConflictWarningDialogTest.kt"
STRINGS = ROOT / "app/src/main/res"


class ConflictContractsTest(unittest.TestCase):
    def test_analyzer_evidence_carries_both_rule_identities(self):
        source = (MAIN / "analysis/AutomationConflictAnalyzer.kt").read_text()
        for field in (
            "candidateRuleId", "candidateRuleName",
            "conflictingRuleId", "conflictingRuleName",
        ):
            self.assertRegex(source, rf"val {field}: String")
        self.assertIn("candidateRuleId = candidate.id", source)
        self.assertIn("candidateRuleName = candidate.name", source)
        self.assertIn("conflictingRuleName = other.name", source)

    def test_create_override_saves_without_second_button_click(self):
        source = (MAIN / "ui/screens/CreateScreen.kt").read_text()
        override = re.search(
            r"onOverride = \{(?P<body>.*?)\n\s*\},\n\s*onDismiss",
            source,
            re.DOTALL,
        )
        self.assertIsNotNone(override)
        assert override is not None
        self.assertIn("pendingSave?.invoke()", override.group("body"))
        self.assertIn("AutomationConflictAnalyzer.analyze(candidate", override.group("body"))
        self.assertIn("current != pendingConflicts", override.group("body"))
        self.assertNotIn("acknowledgedConflicts", source)

    def test_inspect_routes_selected_conflicting_rule_id(self):
        test = TEST.read_text()
        self.assertIn('Text("Rule detail: $inspectedRuleId")', test)
        self.assertIn('onNodeWithText("Rule detail: other").assertIsDisplayed()', test)
        self.assertIn("onInspect = { inspectedRuleId = it }", test)
        app = (MAIN / "ui/App.kt").read_text()
        self.assertIn("inspectRule = { rule -> selectedRule = rule; page = Page.DETAIL }", app)

    def test_english_and_turkish_warning_copy_and_actions_exist(self):
        required = {
            "values": {
                "conflict_warning_title": "Likely automation conflict",
                "conflict_inspect_rule": "Inspect %1$s",
                "conflict_save_anyway": "Save anyway",
                "conflict_enable_anyway": "Enable anyway",
            },
            "values-tr": {
                "conflict_warning_title": "Olası otomasyon çakışması",
                "conflict_inspect_rule": "%1$s kuralını incele",
                "conflict_save_anyway": "Yine de kaydet",
                "conflict_enable_anyway": "Yine de etkinleştir",
            },
        }
        for directory, entries in required.items():
            xml = (STRINGS / directory / "strings.xml").read_text()
            for name, text in entries.items():
                self.assertIn(f'<string name="{name}">{text}</string>', xml)


if __name__ == "__main__":
    unittest.main()
