#!/usr/bin/env python3
"""Run with python scripts/test_locale_contracts.py; no Android SDK needed."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"


class LocaleContractsTest(unittest.TestCase):

    def test_localization_utility_contracts(self):
        localization = (MAIN / "java/com/flowpilot/app/ui/util/Localization.kt").read_text(encoding="utf-8")
        self.assertIn("fun resolveLocaleLanguage(", localization)
        self.assertIn("fun targetLocaleForLanguage(", localization)
        self.assertIn("fun applyAppLocale(", localization)
        self.assertIn("fun android.content.Context.localizedForAppLanguage(", localization)
        self.assertIn("fun android.content.Context.selectedLocaleContext(", localization)

    def test_flowpilot_app_startup_locale_contracts(self):
        app = (MAIN / "java/com/flowpilot/app/FlowPilotApp.kt").read_text(encoding="utf-8")
        self.assertIn("AutomationRepository.getPersistedLanguage(this)", app)
        self.assertIn("applyAppLocale(this, initialLanguage)", app)
        self.assertIn("syncPersistedLanguage()", app)

    def test_automation_service_locale_contracts(self):
        service = (MAIN / "java/com/flowpilot/app/engine/AutomationService.kt").read_text(encoding="utf-8")
        self.assertIn("selectedLocaleContext()", service)
        self.assertIn("refreshNotificationLocale(", service)
        self.assertIn("notif_channel_engine", service)
        self.assertIn("notif_engine_title", service)
        self.assertIn("notif_channel_engine_failure", service)
        self.assertIn("notif_engine_failure_title", service)
        self.assertIn("appLanguage", service)

    def test_automation_repository_locale_contracts(self):
        repo = (MAIN / "java/com/flowpilot/app/data/AutomationRepository.kt").read_text(encoding="utf-8")
        self.assertIn("fun getPersistedLanguage(", repo)
        self.assertIn("fun persistLanguage(", repo)
        self.assertIn("suspend fun syncPersistedLanguage(", repo)
        self.assertIn("applyAppLocale(context, language)", repo)
        self.assertIn("AutomationService.refreshNotificationLocale(context)", repo)

    def test_notification_and_channel_strings_parity(self):
        keys = [
            "notif_channel_engine",
            "notif_channel_engine_desc",
            "notif_engine_title",
            "notif_engine_text",
            "notif_channel_engine_failure",
            "notif_channel_engine_failure_desc",
            "notif_engine_failure_title",
            "notif_engine_failure_text",
        ]
        for locale in ("values", "values-tr"):
            root = ET.parse(MAIN / f"res/{locale}/strings.xml").getroot()
            strings = {node.attrib["name"]: node.text for node in root.findall("string")}
            for key in keys:
                self.assertIn(key, strings)
                self.assertTrue(bool(strings[key] and strings[key].strip()))


if __name__ == "__main__":
    unittest.main()
