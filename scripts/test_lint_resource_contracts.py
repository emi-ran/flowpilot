"""Run with python3 scripts/test_lint_resource_contracts.py; no Android SDK needed."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

MAIN = Path(__file__).resolve().parents[1] / "app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class LintResourceContractsTest(unittest.TestCase):
    def test_location_reader_uses_backported_api(self):
        source = (MAIN / "java/com/flowpilot/app/actions/LocationExecutor.kt").read_text(encoding="utf-8")
        self.assertIn("LocationManagerCompat.isLocationEnabled(it)", source)

    def test_permission_revocation_is_explicitly_handled(self):
        for relative, call in [
            ("actions/MobileDataExecutor.kt", "tm?.isDataEnabled"),
            ("engine/LocationFetcher.kt", "lm.requestLocationUpdates("),
            ("ui/components/WifiPicker.kt", "wm.scanResults"),
        ]:
            with self.subTest(source=relative):
                source = (MAIN / "java/com/flowpilot/app" / relative).read_text(encoding="utf-8")
                next_catch = source[source.index(call):].split("catch (", 1)[1].split(")", 1)[0]
                self.assertIn("SecurityException", next_catch)

    def test_widget_uses_remote_views_supported_classes(self):
        allowed = {"LinearLayout", "ImageView", "ImageButton", "TextView"}
        root = ET.parse(MAIN / "res/layout/widget_flowpilot_control.xml").getroot()
        self.assertEqual(set(node.tag for node in root.iter()) - allowed, set())

    def test_turkish_has_all_translatable_strings(self):
        def names(locale):
            return {node.attrib["name"] for node in ET.parse(MAIN / f"res/{locale}/strings.xml").getroot()
                    if node.tag == "string" and node.get("translatable") != "false"}
        self.assertEqual(names("values") - names("values-tr"), set())

    def test_telephony_is_optional(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        features = {node.get(ANDROID + "name"): node.get(ANDROID + "required")
                    for node in manifest.findall("uses-feature")}
        self.assertEqual(features.get("android.hardware.telephony"), "false")

    def test_conflict_warning_contract(self):
        java = MAIN / "java/com/flowpilot/app"
        analyzer = (java / "analysis/AutomationConflictAnalyzer.kt").read_text(encoding="utf-8")
        expected_actions = {
            "WIFI_ON", "WIFI_OFF", "BLUETOOTH_ON", "BLUETOOTH_OFF",
            "MOBILE_DATA_ON", "MOBILE_DATA_OFF", "AIRPLANE_MODE_ON", "AIRPLANE_MODE_OFF",
            "NFC_ON", "NFC_OFF", "BATTERY_SAVER_ON", "BATTERY_SAVER_OFF",
            "DARK_THEME_ON", "DARK_THEME_OFF", "AUTO_ROTATE_ON", "AUTO_ROTATE_OFF",
            "DND_ON", "DND_OFF", "SOUND_PROFILE_NORMAL", "SOUND_PROFILE_VIBRATE",
            "SOUND_PROFILE_SILENT", "TORCH_ON", "TORCH_OFF", "LOCATION_ON", "LOCATION_OFF",
        }
        self.assertEqual({name for name in expected_actions if f"ActionType.{name}" not in analyzer}, set())
        self.assertNotIn("notificationTitle", analyzer)
        self.assertNotIn("notificationBody", analyzer)
        self.assertNotIn("phoneNumber", analyzer)
        self.assertNotIn("webhook", analyzer.lower())
        for relative in ["ui/screens/CreateScreen.kt", "ui/screens/DetailScreen.kt", "ui/screens/HomeScreen.kt"]:
            self.assertIn("AutomationConflictAnalyzer.analyze", (java / relative).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
