#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
repo = (root / "app/src/main/java/com/flowpilot/app/data/AutomationRepository.kt").read_text()
tests = (root / "app/src/test/java/com/flowpilot/app/data/AutomationRepositoryCryptoTest.kt").read_text()

assert "duplicate_legacyTtsRule_copiesCacheToCloneOwnedFile" in tests
assert "duplicate_ttsRuleWithMissingCache_clearsReferenceWithoutThrowing" in tests
target_exists_test = tests[
    tests.index("fun duplicate_legacyTtsRuleWhenTargetExists_failsWithoutDeletingFilesOrPersistingClone"):
    tests.index("fun duplicate_reencryptsWebhookSecretsWithFreshCiphertext")
]
assert target_exists_test.index("val source = repository.add(") < target_exists_test.index("targetFile.writeBytes(")
assert "ttsManager.computeCacheFileName(" in repo
assert "// Dynamic rules synthesize on demand; never duplicate audio." in repo
assert "createdCloneTtsFile" in repo
assert "createdCloneTtsFile?.delete()" in repo
assert "copyTo(targetFile, overwrite = false)" in repo
assert "ttsAudioFileName = cloneTtsFileName" in repo
assert "if (!targetExisted) targetFile.delete()" in repo
print("TTS clone contract OK")
