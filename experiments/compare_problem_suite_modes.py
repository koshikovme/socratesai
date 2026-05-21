from __future__ import annotations

import argparse
import csv
import json
import statistics
from collections import Counter
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rule-events", required=True)
    parser.add_argument("--ml-events", required=True)
    parser.add_argument("--no-policy-events", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    modes = {
        "RULE": read_csv(args.rule_events),
        "ML": read_csv(args.ml_events),
        "NO_POLICY": read_csv(args.no_policy_events),
    }
    summary = {mode: summarize(rows) for mode, rows in modes.items()}

    comparison_rows = []
    for mode, rows in modes.items():
        for row in rows:
            comparison_rows.append({
                "event_index": row["event_index"],
                "mode": mode,
                "problem_slug": row["problem_slug"],
                "difficulty": row["difficulty"],
                "stage": row["stage"],
                "expected_action": row["expected_action"],
                "system_action": row["action"],
                "agreement": str(row["action"] == row["expected_action"]).lower(),
                "wall_latency_ms": row["wall_latency_ms"],
            })

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(output_dir / "problem_suite_policy_comparison.csv", comparison_rows)
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (output_dir / "summary.md").write_text(to_markdown(summary), encoding="utf-8")


def read_csv(path: str) -> list[dict]:
    with Path(path).open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def summarize(rows: list[dict]) -> dict:
    ok_rows = [row for row in rows if row["status"] == "200"]
    agreements = [row["action"] == row["expected_action"] for row in ok_rows]
    latencies = [int(row["wall_latency_ms"]) for row in ok_rows]
    per_class = per_class_metrics(ok_rows)
    return {
        "events": len(rows),
        "successful": len(ok_rows),
        "errors": len(rows) - len(ok_rows),
        "agreement": round(sum(agreements) / len(agreements), 4) if agreements else 0,
        "macro_f1": per_class["_macro_f1"],
        "mean_latency_ms": round(statistics.mean(latencies), 2) if latencies else 0,
        "p95_latency_ms": percentile(sorted(latencies), 95) if latencies else 0,
        "action_distribution": dict(sorted(Counter(row["action"] for row in ok_rows).items())),
        "per_class": per_class,
        "per_difficulty_agreement": difficulty_agreement(ok_rows),
    }


def difficulty_agreement(rows: list[dict]) -> dict:
    result = {}
    for difficulty in sorted({row["difficulty"] for row in rows}):
        subset = [row for row in rows if row["difficulty"] == difficulty]
        agreements = [row["action"] == row["expected_action"] for row in subset]
        result[difficulty] = round(sum(agreements) / len(agreements), 4) if agreements else 0
    return result


def per_class_metrics(rows: list[dict]) -> dict:
    labels = sorted({row["expected_action"] for row in rows} | {row["action"] for row in rows})
    metrics = {}
    for label in labels:
        tp = sum(1 for row in rows if row["expected_action"] == label and row["action"] == label)
        fp = sum(1 for row in rows if row["expected_action"] != label and row["action"] == label)
        fn = sum(1 for row in rows if row["expected_action"] == label and row["action"] != label)
        precision = tp / (tp + fp) if tp + fp else 0
        recall = tp / (tp + fn) if tp + fn else 0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0
        metrics[label] = {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": sum(1 for row in rows if row["expected_action"] == label),
        }
    metrics["_macro_f1"] = round(statistics.mean(value["f1"] for value in metrics.values()), 4) if metrics else 0
    return metrics


def percentile(sorted_values: list[int], pct: int) -> int:
    if not sorted_values:
        return 0
    index = max(0, min(len(sorted_values) - 1, round((pct / 100) * len(sorted_values) + 0.5) - 1))
    return sorted_values[index]


def to_markdown(summary: dict) -> str:
    lines = [
        "# Problem Suite Policy Comparison",
        "",
        "| Mode | Events | Agreement | Macro F1 | Mean latency | P95 latency |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for mode, values in summary.items():
        lines.append(
            f"| `{mode}` | {values['events']} | {values['agreement']:.2%} | "
            f"{values['macro_f1']:.2%} | {values['mean_latency_ms']} ms | {values['p95_latency_ms']} ms |"
        )

    lines.extend([
        "",
        "## Per-Class F1",
        "",
        "| Mode | CODE_HIGHLIGHT | CONCEPTUAL_HINT | GUIDING_QUESTION | NO_FEEDBACK |",
        "|---|---:|---:|---:|---:|",
    ])
    for mode, values in summary.items():
        per_class = values["per_class"]
        lines.append(
            f"| `{mode}` | {f1(per_class, 'CODE_HIGHLIGHT')} | "
            f"{f1(per_class, 'CONCEPTUAL_HINT')} | {f1(per_class, 'GUIDING_QUESTION')} | "
            f"{f1(per_class, 'NO_FEEDBACK')} |"
        )

    lines.extend([
        "",
        "## Interpretation Boundary",
        "",
        "The benchmark uses a controlled programming problem suite and review-rubric action labels. It is stronger than a narrow smoke test, but it is not a substitute for a classroom learning-gain study.",
        "",
    ])
    return "\n".join(lines)


def f1(per_class: dict, label: str) -> str:
    return f"{per_class.get(label, {}).get('f1', 0):.2%}"


if __name__ == "__main__":
    main()
