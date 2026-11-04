"""Run UI interaction regressions when Node.js is available for development checks."""
from pathlib import Path
import re
import shutil
import subprocess
import unittest

UI = Path(__file__).resolve().parents[1] / "partner_auto/ui.html"


class UIRegressionChecks(unittest.TestCase):
    def test_format_select_offers_every_documented_format(self):
        # A malformed closing tag once swallowed the Java option entirely.
        markup = UI.read_text(encoding="utf-8")
        select = re.search(r'<select id="format">(.*?)</select>', markup, re.S)
        self.assertIsNotNone(select, "format select is missing")
        options = re.findall(r'<option value="([^"]*)"', select.group(1))
        self.assertEqual(options, ["auto", "java", "pp"])
        self.assertNotIn("</option value", markup)

    @unittest.skipUnless(shutil.which("node"), "Node.js is optional for UI development checks")
    def test_drafts_survive_transport_and_source_errors(self):
        result = subprocess.run([shutil.which("node"), str(Path(__file__).with_name("ui_checks.cjs"))],
                                capture_output=True, text=True, encoding="utf-8", timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
