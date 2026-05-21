from __future__ import annotations

import argparse
import csv
from pathlib import Path


RUBRIC = {
    "syntax_missing_semicolon_a": ("CODE_HIGHLIGHT", "First syntax mistake: location cue is enough."),
    "syntax_missing_semicolon_b": ("CONCEPTUAL_HINT", "Repeated syntax issue: add a short syntax reminder."),
    "syntax_missing_semicolon_c": ("CONCEPTUAL_HINT", "Repeated syntax issue: add a short syntax reminder."),
    "off_by_one_first": ("CODE_HIGHLIGHT", "First boundary issue: point to loop condition."),
    "off_by_one_repeat_a": ("CONCEPTUAL_HINT", "Repeated boundary issue: explain loop boundary concept."),
    "off_by_one_repeat_b": ("CONCEPTUAL_HINT", "Repeated boundary issue: explain loop boundary concept."),
    "wrong_condition_first": ("CONCEPTUAL_HINT", "Condition mistake needs conceptual guidance."),
    "wrong_condition_repeat": ("CONCEPTUAL_HINT", "Repeated condition mistake still needs conceptual guidance."),
    "unfinished_todo_first": ("GUIDING_QUESTION", "Unfinished implementation should prompt planning."),
    "unfinished_todo_repeat_a": ("GUIDING_QUESTION", "Repeated unfinished state should prompt reasoning."),
    "unfinished_todo_repeat_b": ("GUIDING_QUESTION", "Unsupported placeholder should prompt next-step reasoning."),
    "complete_return_first": ("NO_FEEDBACK", "Locally complete state should not be interrupted."),
    "unknown_partial_logic": ("CONCEPTUAL_HINT", "Unknown partial logic benefits from a general comparison hint."),
    "complete_return_second": ("NO_FEEDBACK", "Locally complete state should not be interrupted."),
    "syntax_after_progress": ("CODE_HIGHLIGHT", "New syntax issue after progress: location cue is enough."),
    "off_by_one_after_progress": ("CODE_HIGHLIGHT", "New boundary issue after progress: point to condition."),
    "unfinished_todo_after_progress": ("GUIDING_QUESTION", "Unfinished retry should prompt planning."),
    "wrong_condition_after_progress": ("CONCEPTUAL_HINT", "Condition mistake needs conceptual guidance."),
    "complete_return_final": ("NO_FEEDBACK", "Locally complete state should not be interrupted."),
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-csv", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--limit", type=int, default=100)
    args = parser.parse_args()

    with Path(args.events_csv).open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))[:args.limit]

    output_rows = []
    for row in rows:
        expert_action, rationale = RUBRIC[row["scenario"]]
        output_rows.append({
            "event_index": row["event_index"],
            "scenario": row["scenario"],
            "error_type": row["error_type"],
            "system_action": row["action"],
            "expert_action": expert_action,
            "agreement": str(row["action"] == expert_action).lower(),
            "label_source": "pedagogical_rubric_v1",
            "rationale": rationale,
        })

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(output_rows[0].keys()))
        writer.writeheader()
        writer.writerows(output_rows)


if __name__ == "__main__":
    main()
