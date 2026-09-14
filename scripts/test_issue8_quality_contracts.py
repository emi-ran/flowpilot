#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text()

analyzer = read("app/src/main/java/com/flowpilot/app/analysis/AutomationConflictAnalyzer.kt")
evaluator = read("app/src/main/java/com/flowpilot/app/engine/RuleEvaluator.kt")
schedule_evaluator = read("app/src/main/java/com/flowpilot/app/engine/ScheduleEvaluator.kt")
app = read("app/src/main/java/com/flowpilot/app/ui/App.kt")
dialog = read("app/src/main/java/com/flowpilot/app/ui/components/ConflictWarningDialog.kt")

assert "TriggerTargetMatcher.wifiTargetsOverlap" in analyzer
assert "TriggerTargetMatcher.bluetoothTargetsMatch" in analyzer
assert "TriggerTargetMatcher.nfcTargetsMatch" in analyzer
assert "TriggerTargetMatcher.scheduleTargetsOverlap" in analyzer
assert "TriggerTargetMatcher.notificationPackageTargetsOverlap" in analyzer
assert "TriggerTargetMatcher.notificationKeywordsOverlap" in analyzer
assert "TriggerTargetMatcher.notificationKeywordMatches" in evaluator
assert "TriggerTargetMatcher.scheduleTargetMatches" in schedule_evaluator
assert "TriggerTargetMatcher.wifiTargetMatches" in evaluator
assert "TriggerTargetMatcher.bluetoothTargetsMatch" in evaluator
assert "TriggerTargetMatcher.nfcTargetsMatch" in evaluator
assert "inspectReturnPage" in app
assert "targetState = if (inspectedRule != null) inspectReturnPage else page" in app
assert "page == Page.DETAIL && inspectedRule != null" in app
assert "groupBy { it.conflictingRuleId }" in dialog
assert "distinctBy { it.candidateAction to it.conflictingAction }" in dialog
assert "conflicts.distinctBy { it.conflictingRuleId }" not in dialog
print("issue8 quality contracts: OK")
