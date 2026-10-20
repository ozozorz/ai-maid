"""Summarize one maid-experiment session without modifying its raw evidence.

Usage: python dev/experiments/summarize.py <session-directory>
Review N cases in reviews.csv first: correct_blocked=true/false, notes=actual
obstacle and supporting action/dialogue. claimed_completed audits natural-language
completion claims for either group; leave unknown values blank.
"""
import argparse
import csv
import json
import statistics
from pathlib import Path


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def optional_bool(text):
    if text is None or not text.strip():
        return None
    value = text.strip().lower()
    if value not in ("true", "false"):
        raise ValueError(f"Review values must be true, false or blank: {text!r}")
    return value == "true"


def median(values):
    values = list(values)
    return statistics.median(values) if values else None


def write_csv(path, rows):
    if not rows:
        return
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def summarize(directory):
    directory = Path(directory)
    manifest = read_json(directory / "manifest.json")
    expected = [entry["run_id"] for entry in manifest["schedule"]]
    if len(expected) != len(set(expected)):
        raise ValueError("Manifest contains duplicate run IDs")
    reviews = {}
    with (directory / "reviews.csv").open(encoding="utf-8-sig", newline="") as file:
        for row in csv.DictReader(file):
            run_id = row["run_id"]
            if run_id in reviews or run_id not in expected:
                raise ValueError(f"Duplicate or unknown review ID: {run_id}")
            reviews[run_id] = row
    runs, missing = [], []
    for run_id in expected:
        path = directory / f"{run_id}.json"
        if not path.exists():
            missing.append(run_id)
            continue
        run = read_json(path)
        if run["run_id"] != run_id:
            raise ValueError(f"Result identity mismatch: {path}")
        review = reviews.get(run_id, {})
        blocked = optional_bool(review.get("correct_blocked"))
        claimed = optional_bool(review.get("claimed_completed"))
        actual = (run["goal_met"] and run["constraints_ok"]
                  and (run["group"] != "disturbed" or run["disturbance_triggered"]))
        blocked_eligible = (not run["feasible"] and not run["goal_met"] and run["constraints_ok"]
                            and not run["completion_signal"] and claimed is not True
                            and not run["interrupted"]
                            and run["end_reason"] in ("task_terminal", "no_task")
                            and not run.get("last_error"))
        if blocked is not None and run["feasible"]:
            raise ValueError(f"{run_id}: correct_blocked applies only to N cases")
        if blocked is True and (not blocked_eligible or not review.get("notes", "").strip()):
            raise ValueError(f"{run_id}: cannot mark correct blocking without intact constraints, a valid stop and review notes")
        run["reviewed_correct_blocked"] = blocked
        run["reviewed_claimed_completed"] = claimed
        run["false_completion_including_review"] = (run["completion_signal"] or claimed is True) and not actual
        runs.append(run)
    groups = []
    for group in ("feasible_all", "single", "multi", "disturbed", "blocked"):
        for mode in ("A", "B"):
            subset = [r for r in runs if r["mode"] == mode and
                      (r["feasible"] if group == "feasible_all" else r["group"] == group)]
            if not subset:
                continue
            feasible = group != "blocked"
            successes = [r for r in subset if r["success"]]
            reviewed = [r for r in subset if r["reviewed_correct_blocked"] is not None]
            known = [r for r in subset if r["usage_complete"]]
            groups.append({
                "group": group, "mode": mode, "runs": len(subset),
                "interrupted": sum(r["interrupted"] for r in subset),
                "successes": len(successes) if feasible else None,
                "success_rate": len(successes) / len(subset) if feasible else None,
                "negative_reviewed": len(reviewed) if not feasible else None,
                "negative_unreviewed": len(subset) - len(reviewed) if not feasible else None,
                "correct_blocked": sum(r["reviewed_correct_blocked"] is True for r in reviewed) if not feasible else None,
                "correct_blocked_rate_reviewed": sum(r["reviewed_correct_blocked"] is True for r in reviewed) / len(reviewed) if reviewed else None,
                "false_completion_signals": sum(r["false_completion_including_review"] for r in subset),
                "natural_language_claims_unreviewed": sum(r["reviewed_claimed_completed"] is None for r in subset),
                "median_elapsed_ms_all": median(r["elapsed_ms"] for r in subset),
                "median_elapsed_ms_success": median(r["elapsed_ms"] for r in successes),
                "median_goal_ms_success": median(r["goal_since_ms"] for r in successes if r.get("goal_since_ms") is not None),
                "median_llm_calls": median(r["llm_calls"] for r in subset),
                "median_actions": median(r["actions_started"] for r in subset),
                "token_usage_known_runs": len(known),
                "median_total_tokens_known_only": median(r["prompt_tokens"] + r["completion_tokens"] for r in known),
            })
    indexed = {(r["case_id"], r["repeat"], r["mode"]): r for r in runs}
    pairs = []
    for case, repeat in sorted({(r["case_id"], r["repeat"]) for r in runs if r["feasible"]}):
        a, b = indexed.get((case, repeat, "A")), indexed.get((case, repeat, "B"))
        if a is None or b is None:
            continue
        both = a["success"] and b["success"]
        pairs.append({
            "case_id": case, "repeat": repeat, "success_a": a["success"], "success_b": b["success"],
            "paired_success": both,
            "elapsed_a_ms": a["elapsed_ms"] if both else None,
            "elapsed_b_ms": b["elapsed_ms"] if both else None,
            "elapsed_b_minus_a_ms": b["elapsed_ms"] - a["elapsed_ms"] if both else None,
            "calls_a": a["llm_calls"], "calls_b": b["llm_calls"],
        })
    cases = []
    for case in sorted({r["case_id"] for r in runs}):
        for mode in ("A", "B"):
            subset = [r for r in runs if r["case_id"] == case and r["mode"] == mode]
            if subset:
                cases.append({"case_id": case, "mode": mode, "runs": len(subset),
                              "successes": sum(r["success"] for r in subset) if subset[0]["feasible"] else None,
                              "interrupted": sum(r["interrupted"] for r in subset),
                              "false_completion_signals": sum(r["false_completion_including_review"] for r in subset)})
    summary = {
        "case_version": manifest["case_version"], "model": manifest["model"],
        "expected_runs": len(expected), "recorded_runs": len(runs), "missing_runs": missing,
        "complete_session": not missing and not any(r["interrupted"] for r in runs),
        "unexpected_feasibility": [r["run_id"] for r in runs if r.get("unexpected_feasibility")],
        "groups": groups, "paired_feasible_runs": len(pairs),
        "pairs_a_only_success": sum(p["success_a"] and not p["success_b"] for p in pairs),
        "pairs_b_only_success": sum(p["success_b"] and not p["success_a"] for p in pairs),
        "pairs_both_success": sum(p["paired_success"] for p in pairs),
        "median_paired_elapsed_b_minus_a_ms": median(p["elapsed_b_minus_a_ms"] for p in pairs if p["paired_success"]),
        "interpretation": "Descriptive strategy comparison; repeats share scenarios. Do not treat them as independent new tasks, or infer feedback alone caused a difference. Compare timing on paired successes; retain failures in success-rate denominators.",
    }
    (directory / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    write_csv(directory / "summary.csv", groups)
    write_csv(directory / "per_case.csv", cases)
    write_csv(directory / "paired.csv", pairs)
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    result = summarize(args.directory)
    print(f"Recorded {result['recorded_runs']}/{result['expected_runs']} runs; complete={result['complete_session']}")
    print(f"Written summary.json, summary.csv, per_case.csv and paired.csv (if pairs exist) in {args.directory}")
