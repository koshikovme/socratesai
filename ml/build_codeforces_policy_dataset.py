from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
REPO_DIR = BASE_DIR.parent
DEFAULT_DATASETS_DIR = REPO_DIR / "datasets" / "codeforces-submissions"
DEFAULT_OUTPUT = BASE_DIR / "codeforces_policy_dataset.csv"

OUTPUT_COLUMNS = [
    "submission_id",
    "problem_id",
    "programming_language",
    "verdict",
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
    "target_feedback_action",
]


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Build an auxiliary SocratesAI policy dataset from the local "
            "open-r1/codeforces-submissions selected_accepted and selected_incorrect subsets."
        )
    )
    parser.add_argument(
        "--datasets-dir",
        default=str(DEFAULT_DATASETS_DIR),
        help="Path to datasets/codeforces-submissions.",
    )
    parser.add_argument(
        "--output",
        default=str(DEFAULT_OUTPUT),
        help="Output CSV path.",
    )
    parser.add_argument(
        "--max-accepted",
        type=int,
        default=None,
        help="Optional cap for accepted rows.",
    )
    parser.add_argument(
        "--max-incorrect",
        type=int,
        default=None,
        help="Optional cap for incorrect rows.",
    )
    args = parser.parse_args()

    datasets_dir = Path(args.datasets_dir)
    accepted_path = datasets_dir / "selected_accepted" / "train-00000-of-00001.parquet"
    incorrect_path = datasets_dir / "selected_incorrect" / "train-00000-of-00001.parquet"

    accepted = read_subset(accepted_path, args.max_accepted)
    incorrect = read_subset(incorrect_path, args.max_incorrect)

    rows = pd.concat(
        [
            transform_rows(accepted, source_name="selected_accepted"),
            transform_rows(incorrect, source_name="selected_incorrect"),
        ],
        ignore_index=True,
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    rows.to_csv(output, index=False)

    summary = {
        "output": str(output),
        "rows": int(len(rows)),
        "source_rows": {
            "selected_accepted": int(len(accepted)),
            "selected_incorrect": int(len(incorrect)),
        },
        "problem_count": int(rows["problem_id"].nunique()),
        "language_count": int(rows["programming_language"].nunique()),
        "verdict_distribution": count_values(rows["verdict"]),
        "target_distribution": count_values(rows["target_feedback_action"]),
        "language_distribution_top10": count_values(rows["programming_language"], limit=10),
        "interpretation": (
            "This is an auxiliary verdict-derived policy dataset. It uses real human "
            "programming submissions, but its target actions are weak labels derived "
            "from judge verdicts and passed-test counts, not instructor feedback labels."
        ),
    }

    summary_path = output.with_name(f"{output.stem}_summary.json")
    report_path = output.with_name(f"{output.stem}_report.md")
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    report_path.write_text(build_report(summary), encoding="utf-8")

    print(f"Saved dataset: {output.resolve()}")
    print(f"Saved summary: {summary_path.resolve()}")
    print(f"Saved report: {report_path.resolve()}")


def read_subset(path: Path, limit: int | None) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"Dataset subset not found: {path}")

    columns = [
        "submission_id",
        "source",
        "problem_id",
        "programmingLanguage",
        "verdict",
        "passedTestCount",
        "timeConsumedMillis",
        "memoryConsumedBytes",
    ]
    df = pd.read_parquet(path, columns=columns)
    if limit is not None:
        df = df.head(limit)
    return df


def transform_rows(df: pd.DataFrame, source_name: str) -> pd.DataFrame:
    rows = []
    for row in df.itertuples(index=False):
        verdict = normalize_text(row.verdict)
        passed = int(row.passedTestCount) if pd.notna(row.passedTestCount) else 0
        source = row.source if isinstance(row.source, str) else ""
        error_type, severity, compile_success, tests_failed, action = map_verdict(verdict, passed)

        rows.append(
            {
                "submission_id": str(row.submission_id),
                "problem_id": str(row.problem_id),
                "programming_language": normalize_text(row.programmingLanguage),
                "verdict": verdict,
                "dataset_source": source_name,
                "error_type": error_type,
                "severity": severity,
                "compile_success": compile_success,
                "tests_passed": passed,
                "tests_failed": tests_failed,
                "same_error_count": 1,
                "total_errors_seen": 1,
                "attempt_no": 1,
                "last_feedback_action": "NONE",
                "last_feedback_success": False,
                "has_suspicious_region": action != "NO_FEEDBACK",
                "code_lines": count_code_lines(source),
                "total_feedback_count_in_session": 0,
                "target_feedback_action": action,
            }
        )

    return pd.DataFrame(rows)


def map_verdict(verdict: str, passed_tests: int) -> tuple[str, str, bool, int, str]:
    if verdict == "OK":
        return "SUCCESS", "LOW", True, 0, "NO_FEEDBACK"

    if verdict == "COMPILATION_ERROR":
        return "SYNTAX_ERROR", "HIGH", False, 1, "CODE_HIGHLIGHT"

    if verdict in {"RUNTIME_ERROR", "CRASHED", "MEMORY_LIMIT_EXCEEDED"}:
        return "POSSIBLE_NULL_ACCESS", "HIGH", True, 1, "CODE_HIGHLIGHT"

    if verdict in {"TIME_LIMIT_EXCEEDED", "IDLENESS_LIMIT_EXCEEDED"}:
        return "WRONG_CONDITION", "MEDIUM", True, 1, "CONCEPTUAL_HINT"

    if verdict in {"WRONG_ANSWER", "PARTIAL", "FAILED", "REJECTED", "CHALLENGED", "SKIPPED"}:
        if passed_tests <= 1:
            return "STUCK_NO_PROGRESS", "MEDIUM", True, 1, "GUIDING_QUESTION"
        return "WRONG_CONDITION", "MEDIUM", True, 1, "CONCEPTUAL_HINT"

    return "UNKNOWN", "LOW", True, 1, "CONCEPTUAL_HINT"


def count_code_lines(source: str) -> int:
    if not source.strip():
        return 0
    return len(source.splitlines())


def normalize_text(value) -> str:
    if pd.isna(value):
        return ""
    return str(value).strip()


def count_values(series: pd.Series, limit: int | None = None) -> dict[str, int]:
    counts = series.astype(str).value_counts(dropna=False)
    if limit is not None:
        counts = counts.head(limit)
    return {str(label): int(count) for label, count in counts.items()}


def build_report(summary: dict) -> str:
    lines = [
        "# Codeforces Auxiliary Policy Dataset",
        "",
        f"- Output: `{summary['output']}`",
        f"- Rows: {summary['rows']}",
        f"- Problems: {summary['problem_count']}",
        f"- Languages: {summary['language_count']}",
        "",
        "## Source Rows",
        "",
        "| Source | Rows |",
        "|---|---:|",
    ]
    for source, count in summary["source_rows"].items():
        lines.append(f"| `{source}` | {count} |")

    lines.extend([
        "",
        "## Target Distribution",
        "",
        "| Target action | Rows |",
        "|---|---:|",
    ])
    for label, count in summary["target_distribution"].items():
        lines.append(f"| `{label}` | {count} |")

    lines.extend([
        "",
        "## Verdict Distribution",
        "",
        "| Verdict | Rows |",
        "|---|---:|",
    ])
    for label, count in summary["verdict_distribution"].items():
        lines.append(f"| `{label}` | {count} |")

    lines.extend([
        "",
        "## Interpretation Boundary",
        "",
        summary["interpretation"],
        "",
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    main()
