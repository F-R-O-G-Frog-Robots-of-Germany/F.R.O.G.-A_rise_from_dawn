"""Whole-file pastes, exact local dependency lookup and original source locations."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from partner_auto.app import import_sources
from partner_auto.java_sources import split_units, local_frog_helpers, restore_locations

ROOT = Path(__file__).resolve().parents[3]


class JavaSourcePreparationChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = ROOT / "TeamCode/src/main/java/org/firstinspires/ftc/teamcode"
        paths = [root / "opmodes/auto/structure/AbstractAuto.java",
                 root / "opmodes/auto/structure/OptimalPoses.java",
                 root / "opmodes/auto/comp/BothFlowersNoGarden.java"]
        cls.exact_sources = {file.name: file.read_text(encoding="utf-8-sig") for file in paths}

    def test_users_three_separate_files_resolve_and_extract(self):
        result = import_sources(self.exact_sources)
        self.assertEqual(result["status"], "complete", result["diagnostics"])
        self.assertEqual(sum(a["kind"] == "move" for a in result["actions"]), 5)
        self.assertEqual(sum(a["kind"] == "shoot" for a in result["actions"]), 3)
        self.assertEqual(result["start_pose"]["x"], 59.25)
        self.assertIn("LOCAL_FROG_HELPERS", [d["code"] for d in result["diagnostics"]])
        self.assertEqual(result["input_files"], list(self.exact_sources))

    def test_users_three_complete_files_in_one_paste(self):
        text = "\n\n".join(self.exact_sources.values())
        result = import_sources({"Pasted.java": text})
        self.assertEqual(result["status"], "complete", result["diagnostics"])
        self.assertEqual(len(result["actions"]), 8)
        self.assertIn("COMBINED_JAVA_SPLIT", [d["code"] for d in result["diagnostics"]])
        for action in result["actions"]:
            source = action["source"]
            self.assertEqual(source["file"], "Pasted.java")
            self.assertGreater(source["line"], 1)
        self.assertEqual(text, "\n\n".join(self.exact_sources.values()), "Input text must not be modified")

    def test_package_words_in_comments_strings_and_text_blocks_do_not_split(self):
        source = '''package real;
        // package pretend.comment;
        /* package pretend.block; */
        class A { String x = "package pretend.string;";
          String block = """
          package pretend.textblock;
          """;
        }
        package second; class B {}
        '''
        files, origins, diagnostics = split_units({"paste.java": source})
        self.assertEqual(len(files), 2)
        self.assertEqual(len(origins), 2)
        self.assertEqual(len(diagnostics), 1)
        result = {"source": {"file": list(files)[1], "line": 1}}
        restore_locations(result, origins)
        self.assertEqual(result["source"]["file"], "paste.java")
        self.assertEqual(result["source"]["line"], 9)

    def test_unknown_partner_does_not_receive_local_lookalike_classes(self):
        supplied = {"Auto.java": "package other; @Autonomous class Auto extends PartnerBase {}"}
        result, diagnostics = local_frog_helpers(supplied)
        self.assertEqual(result, supplied)
        self.assertEqual(diagnostics, [])

    def test_conflicting_supplied_frog_class_disables_local_fallback(self):
        supplied = dict(self.exact_sources)
        supplied["OptimalPoses.java"] = supplied["OptimalPoses.java"].replace("FIELD_FIX = true", "FIELD_FIX = false")
        result, diagnostics = local_frog_helpers(supplied)
        self.assertEqual(result, supplied)
        self.assertEqual(diagnostics, [])


if __name__ == "__main__":
    unittest.main()
