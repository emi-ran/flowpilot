#!/usr/bin/env python3
"""Static contracts for safe rule duplication; no Android SDK required."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = (ROOT / "app/src/main/java/com/flowpilot/app/data/AutomationRepository.kt").read_text()
VIEW_MODEL = (ROOT / "app/src/main/java/com/flowpilot/app/ui/AppViewModel.kt").read_text()
APP = (ROOT / "app/src/main/java/com/flowpilot/app/ui/App.kt").read_text()
HOME = (ROOT / "app/src/main/java/com/flowpilot/app/ui/screens/HomeScreen.kt").read_text()


def require(text: str, needle: str) -> None:
    assert needle in text, f"missing contract: {needle}"


for reset in (
    "id = newId",
    "name = copyName",
    "enabled = false",
    "createdAt = createdAt",
    "lastTriggeredAt = 0L",
):
    require(REPOSITORY, reset)
require(REPOSITORY, "firstOrNull { it.id == sourceId }?.withDecryptedSecrets()")
require(REPOSITORY, "clone!!.withEncryptedSecrets()")
for repository_contract in (
    "throw java.io.IOException(\"Source TTS cache file is missing\")",
    "catch (error: java.io.IOException)",
    "throw error",
    "catch (error: Throwable)",
):
    require(REPOSITORY, repository_contract)
duplicate_body = REPOSITORY[REPOSITORY.index("suspend fun duplicate("):REPOSITORY.index("suspend fun update(")]
assert duplicate_body.count("throw error") == 2, "duplicate must rethrow copy I/O and outer cancellation/serious failures"
assert "catch (_: Throwable)" not in REPOSITORY[REPOSITORY.index("suspend fun duplicate("):REPOSITORY.index("suspend fun update(")]
for view_model_contract in (
    "fun duplicateRule(",
    "source: Automation",
    "copyName: String",
    "onResult: (Result<Automation>) -> Unit",
    "onResult(duplicateRuleResult { repository.duplicate(source.id, copyName) })",
):
    require(VIEW_MODEL, view_model_contract)
require(HOME, "vm.duplicateRule(item.rule, copyName) { result ->")
require(HOME, "result.onSuccess(detail).onFailure")
require(HOME, "duplicate_rule_failed")
require(HOME, "R.string.btn_duplicate")
require(APP, "detail = { selectedRule = it; page = Page.DETAIL }")
print("rule duplicate static contracts: OK")
