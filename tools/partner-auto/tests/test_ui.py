"""Run UI interaction regressions when Node.js is available for development checks."""
from pathlib import Path
import shutil
import subprocess
import unittest


class UIRegressionChecks(unittest.TestCase):
    @unittest.skipUnless(shutil.which("node"), "Node.js is optional for UI development checks")
    def test_drafts_survive_transport_and_source_errors(self):
        result = subprocess.run([shutil.which("node"), str(Path(__file__).with_name("ui_checks.cjs"))],
                                capture_output=True, text=True, encoding="utf-8", timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
