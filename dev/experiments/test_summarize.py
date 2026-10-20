import csv
import json
import tempfile
import unittest
from pathlib import Path

from summarize import summarize


class SummaryTest(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1] / "devrun" / "build" / "python-tests"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)

    def fixture(self, runs, missing=()):
        ids = [r["run_id"] for r in runs] + list(missing)
        (self.path / "manifest.json").write_text(json.dumps({
            "case_version": "test", "model": "mock", "schedule": [{"run_id": i} for i in ids]}))
        for run in runs:
            (self.path / f"{run['run_id']}.json").write_text(json.dumps(run))
        self.reviews([{"run_id": i} for i in ids])

    def reviews(self, rows):
        with (self.path / "reviews.csv").open("w", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=["run_id", "correct_blocked", "claimed_completed", "notes"])
            writer.writeheader()
            writer.writerows(rows)

    def run_row(self, case, mode, success, elapsed=10):
        feasible = not case.startswith("N")
        return dict(run_id=f"{case}-1-{mode}", case_id=case, repeat=1, mode=mode,
                    group="single" if feasible else "blocked", feasible=feasible,
                    goal_met=success, success=success, constraints_ok=True,
                    completion_signal=success, disturbance_triggered=False, interrupted=False,
                    end_reason="task_terminal", elapsed_ms=elapsed, goal_since_ms=elapsed,
                    llm_calls=2, actions_started=1, usage_complete=False, prompt_tokens=None, completion_tokens=None)

    def test_negatives_and_failed_time_excluded_from_success_timing(self):
        self.fixture([self.run_row("S1", "A", True, 10), self.run_row("S1", "B", False, 1),
                      self.run_row("N1", "A", False), self.run_row("N1", "B", False)])
        summary = summarize(self.path)
        positives = [r for r in summary["groups"] if r["group"] == "feasible_all"]
        self.assertEqual([1, 1], [r["runs"] for r in positives])
        self.assertEqual([1, 0], [r["success_rate"] for r in positives])
        self.assertIsNone(positives[1]["median_elapsed_ms_success"])
        self.assertEqual(0, summary["pairs_both_success"])
        self.assertIsNone(summary["median_paired_elapsed_b_minus_a_ms"])
        self.assertIsNone(positives[0]["median_total_tokens_known_only"])
        self.assertEqual(1, next(r for r in summary["groups"] if r["group"] == "blocked")["negative_unreviewed"])

    def test_missing_runs_are_visible_and_raw_files_unchanged(self):
        self.fixture([self.run_row("S1", "A", True)], ["S1-1-B"])
        raw = (self.path / "S1-1-A.json").read_bytes()
        summary = summarize(self.path)
        self.assertFalse(summary["complete_session"])
        self.assertEqual(["S1-1-B"], summary["missing_runs"])
        self.assertEqual(raw, (self.path / "S1-1-A.json").read_bytes())

    def test_correct_blocking_requires_review_evidence_and_valid_stop(self):
        row = self.run_row("N1", "A", False)
        self.fixture([row])
        self.reviews([dict(run_id="N1-1-A", correct_blocked="true")])
        with self.assertRaises(ValueError):
            summarize(self.path)
        self.reviews([dict(run_id="N1-1-A", correct_blocked="true", notes="No wheat or mature crop, explained in dialogue.")])
        self.assertEqual(1, summarize(self.path)["groups"][0]["correct_blocked"])
        row["end_reason"] = "timeout"
        (self.path / "N1-1-A.json").write_text(json.dumps(row))
        with self.assertRaises(ValueError):
            summarize(self.path)


if __name__ == "__main__":
    unittest.main()
