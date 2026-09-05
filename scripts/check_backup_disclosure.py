"""Source contracts; run with python3 scripts/check_backup_disclosure.py. No Gradle."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
ui = MAIN / "java/com/flowpilot/app/ui"
vm = (ui / "AppViewModel.kt").read_text()
share = vm[vm.index("    fun shareRule("):vm.index("    /**", vm.index("    fun shareRule("))]
assert "Intent.EXTRA_TEXT" not in share
assert share.count("putExtra(Intent.EXTRA_STREAM, uri)") == 2
screens = ui / "screens"
settings = (screens / "SettingsScreen.kt").read_text()
assert "remember(selectedStrategy) { mutableStateOf(false) }" in settings
assert "enabled = selectedStrategy != ImportStrategy.REPLACE_ALL || replaceAcknowledged" in settings
assert "if (selectedStrategy != ImportStrategy.REPLACE_ALL || replaceAcknowledged)" in settings
assert "Checkbox(checked = replaceAcknowledged" in settings
for screen in ("HomeScreen.kt", "DetailScreen.kt", "SettingsScreen.kt"):
    assert "BackupDisclosureDialog(" in (screens / screen).read_text(), screen
for locale in ("values", "values-tr"):
    strings = {node.attrib['name']: node.text for node in ET.parse(MAIN / f"res/{locale}/backup_disclosure.xml").getroot()}
    assert len(strings) == 6
    assert all(strings.values())
body_node = ET.parse(MAIN / "res/values/backup_disclosure.xml").getroot().find("string[@name='backup_disclosure_body']")
assert body_node is not None and body_node.text
body = body_node.text
for phrase in ("unencrypted", "Webhook URLs", "headers", "bodies", "phone numbers", "SMS", "Wi-Fi", "Bluetooth", "NFC", "cannot fully restore"):
    assert phrase in body, phrase
print("PASS: attachment-only share, export disclosure coverage, replace acknowledgement guard, EN/TR resources")
