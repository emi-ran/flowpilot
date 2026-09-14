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

    def test_blank_name_create_candidate_matches_saved_automatic_name(self):
        source = (MAIN / "ui/screens/CreateScreen.kt").read_text()
        kotlin_test = (ROOT / "app/src/test/java/com/flowpilot/app/ui/util/AutomationNameGeneratorTest.kt").read_text()
        self.assertIn("fun blankName_createCandidateUsesSavedAutomaticName()", kotlin_test)
        self.assertIn("val finalName = name.ifBlank", source)
        self.assertIn("automaticAutomationName(", source)
        self.assertGreaterEqual(source.count("name = finalName"), 2)

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
        self.assertIn(
            """inspectRule = { rule ->
                        inspectedRule = rule
                        inspectReturnPage = Page.CREATE
                        page = Page.DETAIL""",
            app,
        )
        self.assertIn("targetState = if (inspectedRule != null) inspectReturnPage else page", app)
        self.assertIn("""inspectedRule = null
                page = inspectReturnPage""", app)

    def test_home_inspect_preserves_pending_enable_warning_in_root(self):
        app = (MAIN / "ui/App.kt").read_text()
        home = (MAIN / "ui/screens/HomeScreen.kt").read_text()
        navigation_test = (ROOT / "app/src/androidTest/java/com/flowpilot/app/FlowPilotRootConflictNavigationTest.kt").read_text()
        self.assertIn("fun homeEnableInspectBackReturnsToPendingWarningAndAllowsOverride()", navigation_test)
        self.assertIn('testTag("rule-enabled-${item.rule.id}")', home)
        self.assertIn("inspectRule: (Automation) -> Unit", home)
        self.assertIn("?.rule?.let(inspectRule)", home)
        self.assertIn(
            """inspectRule = { rule ->
                        inspectedRule = rule
                        inspectReturnPage = Page.HOME
                        page = Page.DETAIL""",
            app,
        )

    def test_inspect_hides_warning_without_clearing_pending_state(self):
        app = (MAIN / "ui/App.kt").read_text()
        navigation_test = (ROOT / "app/src/androidTest/java/com/flowpilot/app/FlowPilotRootConflictNavigationTest.kt").read_text()
        self.assertEqual(app.count("showConflictWarning = inspectedRule == null"), 3)
        self.assertIn('testTag("rule-detail-${initialRule.id}")', (MAIN / "ui/screens/DetailScreen.kt").read_text())
        self.assertGreaterEqual(
            navigation_test.count('onNodeWithText("Likely automation conflict").assertDoesNotExist()'),
            3,
        )
        self.assertGreaterEqual(
            navigation_test.count('onNodeWithTag("rule-detail-other").assertIsDisplayed()'),
            3,
        )
        self.assertIn("fun createInspectBackReturnsToPendingWarningAndAllowsOverride()", navigation_test)

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
