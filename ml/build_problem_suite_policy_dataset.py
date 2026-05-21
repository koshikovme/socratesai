from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


OUTPUT_COLUMNS = [
    "dataset_source",
    "source_mode",
    "source_event_index",
    "cohort",
    "student_id",
    "session_id",
    "task_id",
    "problem_id",
    "problem_slug",
    "problem_title",
    "difficulty",
    "concept",
    "stage",
    "scenario",
    "error_type",
    "severity",
    "compile_success",
    "tests_passed",
    "tests_failed",
    "same_error_count",
    "total_errors_seen",
    "attempt_no",
    "last_feedback_action",
    "last_feedback_success",
    "has_suspicious_region",
    "code_lines",
    "total_feedback_count_in_session",
    "analysis_time_ms",
    "total_latency_ms",
    "feedback_action",
    "target_feedback_action",
    "agreement",
    "suspicious_region",
]


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build a supervised policy-training CSV from problem-suite HTTP logs."
    )
    parser.add_argument(
        "--events",
        required=True,
        action="append",
        help="Path to an events.csv file produced by experiments/problem_suite_http_benchmark.py. Can be repeated.",
    )
    parser.add_argument(
        "--output",
        default="ml/problem_suite_policy_dataset.csv",
        help="Output CSV path.",
    )
    parser.add_argument(
        "--summary",
        default=None,
        help="Optional JSON summary path. Defaults to <output stem>_summary.json.",
    )
    parser.add_argument(
        "--report",
        default=None,
        help="Optional Markdown report path. Defaults to <output stem>_report.md.",
    )
    parser.add_argument(
        "--source-name",
        default="problem_suite_http",
        help="Dataset source label written into the output CSV.",
    )
    parser.add_argument(
        "--mode-filter",
        default=None,
        help="Optional source mode filter, for example RULE or ML.",
    )
    args = parser.parse_args()

    input_rows = []
    for event_path in args.events:
        input_rows.extend(read_event_rows(Path(event_path), args.mode_filter))

    if not input_rows:
        raise ValueError("No successful problem-suite rows were found.")

    output_rows = build_policy_rows(input_rows, args.source_name)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    write_csv(output_path, output_rows)

    summary = summarize(output_rows, [Path(path) for path in args.events])
    summary_path = Path(args.summary) if args.summary else output_path.with_name(f"{output_path.stem}_summary.json")
    report_path = Path(args.report) if args.report else output_path.with_name(f"{output_path.stem}_report.md")
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    report_path.write_text(to_markdown(summary), encoding="utf-8")

    print(f"Read event rows: {len(input_rows)}")
    print(f"Wrote dataset rows: {len(output_rows)}")
    print(f"Saved dataset to: {output_path.resolve()}")
    print(f"Saved summary to: {summary_path.resolve()}")
    print(f"Saved report to: {report_path.resolve()}")


def read_event_rows(path: Path, mode_filter: str | None) -> list[dict]:
    if not path.exists():
        raise FileNotFoundError(f"Events file not found: {path.resolve()}")

    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    successful = []
    for row in rows:
        if str(row.get("status", "")).strip() != "200":
            continue
        if mode_filter and str(row.get("mode", "")).upper() != mode_filter.upper():
            continue
        successful.append(row)
    return successful


def build_policy_rows(rows: list[dict], source_name: str) -> list[dict]:
    grouped: dict[tuple[str, str, str, str], list[dict]] = defaultdict(list)
    for row in rows:
        key = (
            str(row.get("mode", "")),
            str(row.get("cohort", "")),
            str(row.get("student_id", "")),
            str(row.get("problem_id", "")),
        )
        grouped[key].append(row)

    output_rows = []
    for key, session_rows in grouped.items():
        session_rows = sorted(session_rows, key=lambda row: to_int(row.get("attempt_no"), 0))
        previous_rows: list[dict] = []
        mode, cohort, student_id, problem_id = key
        session_id = f"{source_name}:{mode}:c{cohort}:s{student_id}:p{problem_id}"

        for row in session_rows:
            error_type = value(row, "error_type", "UNKNOWN")
            previous = previous_rows[-1] if previous_rows else None
            policy_row = {
                "dataset_source": source_name,
                "source_mode": mode,
                "source_event_index": value(row, "event_index"),
                "cohort": cohort,
                "student_id": student_id,
                "session_id": session_id,
                "task_id": problem_id,
                "problem_id": problem_id,
                "problem_slug": value(row, "problem_slug"),
                "problem_title": value(row, "problem_title"),
                "difficulty": value(row, "difficulty"),
                "concept": value(row, "concept"),
                "stage": value(row, "stage"),
                "scenario": value(row, "scenario"),
                "error_type": error_type,
                "severity": infer_severity(row),
                "compile_success": normalize_bool(value(row, "compile_success")),
                "tests_passed": to_int(row.get("tests_passed"), 0),
                "tests_failed": to_int(row.get("tests_failed"), 0),
                "same_error_count": consecutive_same_error_count(previous_rows, error_type),
                "total_errors_seen": len(previous_rows),
                "attempt_no": to_int(row.get("attempt_no"), len(previous_rows) + 1),
                "last_feedback_action": value(previous, "action") if previous else "",
                "last_feedback_success": value(previous, "agreement") if previous else "",
                "has_suspicious_region": bool(value(row, "suspicious_region")),
                "code_lines": max(1, to_int(row.get("code_lines"), 1)),
                "total_feedback_count_in_session": len(previous_rows),
                "analysis_time_ms": to_int(row.get("analysis_time_ms"), 0),
                "total_latency_ms": to_int(row.get("wall_latency_ms"), 0),
                "feedback_action": value(row, "action"),
                "target_feedback_action": value(row, "expected_action"),
                "agreement": value(row, "agreement"),
                "suspicious_region": value(row, "suspicious_region"),
            }
            output_rows.append(policy_row)
            previous_rows.append(row)

    return sorted(output_rows, key=lambda row: to_int(row["source_event_index"], 0))


def infer_severity(row: dict) -> str:
    error_type = value(row, "error_type", "UNKNOWN")
    stage = value(row, "stage")
    expected_action = value(row, "expected_action")
    compile_success = normalize_bool(value(row, "compile_success"))

    if compile_success == "False" or error_type == "SYNTAX_ERROR":
        return "HIGH"
    if stage == "local_completion" or expected_action == "NO_FEEDBACK":
        return "LOW"
    if error_type == "STUCK_NO_PROGRESS":
        return "LOW"
    return "MEDIUM"


def consecutive_same_error_count(previous_rows: list[dict], current_error_type: str) -> int:
    count = 1
    for row in reversed(previous_rows):
        if value(row, "error_type", "UNKNOWN") != current_error_type:
            break
        count += 1
    return count


def summarize(rows: list[dict], source_paths: list[Path]) -> dict:
    by_problem = Counter(row["problem_slug"] for row in rows)
    by_stage = Counter(row["stage"] for row in rows)
    target_distribution = Counter(row["target_feedback_action"] for row in rows)
    actual_distribution = Counter(row["feedback_action"] for row in rows)
    agreements = [row["agreement"] == "true" for row in rows]

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_files": [str(path) for path in source_paths],
        "rows": len(rows),
        "sessions": len({row["session_id"] for row in rows}),
        "problems": len(by_problem),
        "agreement_with_rubric": round(sum(agreements) / len(agreements), 4) if agreements else 0,
        "target_distribution": dict(sorted(target_distribution.items())),
        "actual_distribution": dict(sorted(actual_distribution.items())),
        "problem_distribution": dict(sorted(by_problem.items())),
        "stage_distribution": dict(sorted(by_stage.items())),
        "notes": [
            "target_feedback_action comes from the explicit problem-suite review rubric.",
            "feedback_action is the action returned by the running application.",
            "last_feedback_success uses previous event rubric agreement as an offline proxy because classroom outcome labels are not available in this controlled suite.",
        ],
    }


def to_markdown(summary: dict) -> str:
    lines = [
        "# Problem-Suite Policy Dataset",
        "",
        f"- Generated at: {summary['generated_at']}",
        f"- Rows: {summary['rows']}",
        f"- Sessions: {summary['sessions']}",
        f"- Programming problems: {summary['problems']}",
        f"- Source files: {', '.join(summary['source_files'])}",
        f"- App action vs rubric agreement: {summary['agreement_with_rubric']:.2%}",
        "",
        "## Target Label Distribution",
        "",
        "| Action | Rows |",
        "|---|---:|",
    ]
    for action, count in summary["target_distribution"].items():
        lines.append(f"| `{action}` | {count} |")

    lines.extend([
        "",
        "## Actual App Action Distribution",
        "",
        "| Action | Rows |",
        "|---|---:|",
    ])
    for action, count in summary["actual_distribution"].items():
        lines.append(f"| `{action}` | {count} |")

    lines.extend([
        "",
        "## Problem Coverage",
        "",
        "| Problem | Rows |",
        "|---|---:|",
    ])
    for problem, count in summary["problem_distribution"].items():
        lines.append(f"| `{problem}` | {count} |")

    lines.extend([
        "",
        "## Provenance",
        "",
        "`target_feedback_action` is the supervised label from the problem-suite review rubric. "
        "`feedback_action` is the action returned by the running application through the real HTTP endpoint. "
        "This dataset is a controlled technical pilot dataset, not a semester-long classroom dataset.",
        "",
    ])
    return "\n".join(lines)


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def value(row: dict | None, key: str, default: str = "") -> str:
    if row is None:
        return default
    raw = row.get(key, default)
    if raw is None:
        return default
    return str(raw).strip()


def normalize_bool(raw: str) -> str:
    text = str(raw).strip().lower()
    if text in {"true", "1", "yes"}:
        return "True"
    if text in {"false", "0", "no"}:
        return "False"
    return ""


def to_int(raw, default: int) -> int:
    try:
        text = str(raw).strip()
        if text == "":
            return default
        return int(float(text))
    except (TypeError, ValueError):
        return default


if __name__ == "__main__":
    main()
