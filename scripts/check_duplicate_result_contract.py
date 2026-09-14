#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/flowpilot/app/ui/AppViewModel.kt").read_text()
test = (root / "app/src/test/java/com/flowpilot/app/ui/DuplicateRuleResultTest.kt").read_text()

assert "duplicateRuleResult_missingSource_returnsFailure" in test
assert "duplicateRuleResult_repositoryError_returnsFailure" in test
assert "duplicateRuleResult_createdClone_returnsSuccess" in test
assert "suspend fun duplicateRuleResult" in source
assert "onResult: (Result<Automation>) -> Unit" in source
home = (root / "app/src/main/java/com/flowpilot/app/ui/screens/HomeScreen.kt").read_text()
assert "duplicate_rule_failed" in home
assert "SnackbarHost(snackbarHostState)" in home
print("Duplicate result contract OK")
