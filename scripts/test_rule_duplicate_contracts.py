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
for view_model_contract in ("fun duplicateRule(", "source: Automation", "copyName: String", "onDuplicated: (Automation) -> Unit"):
    require(VIEW_MODEL, view_model_contract)
require(HOME, "onDuplicate = { vm.duplicateRule(item.rule, copyName, detail) }")
require(HOME, "R.string.btn_duplicate")
require(APP, "detail = { selectedRule = it; page = Page.DETAIL }")
print("rule duplicate static contracts: OK")
