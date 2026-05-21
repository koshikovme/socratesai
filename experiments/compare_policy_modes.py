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
    parser.add_argument("--labels", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--limit", type=int, default=100)
    args = parser.parse_args()

    labels = {int(row["event_index"]): row for row in read_csv(args.labels)[:args.limit]}
    modes = {
        "RULE": read_csv(args.rule_events)[:args.limit],
        "ML": read_csv(args.ml_events)[:args.limit],
        "NO_POLICY": read_csv(args.no_policy_events)[:args.limit],
    }

    comparison_rows = []
    summary = {}
    for mode, rows in modes.items():
        agreements = []
        latencies = []
        actions = Counter()
        for row in rows:
            event_index = int(row["event_index"])
            expert_action = labels[event_index]["expert_action"]
            system_action = row["action"]
            agreements.append(system_action == expert_action)
            latencies.append(int(row["wall_latency_ms"]))
            actions[system_action] += 1
            comparison_rows.append({
                "event_index": event_index,
                "mode": mode,
                "scenario": row["scenario"],
                "expert_action": expert_action,
                "system_action": system_action,
                "agreement": str(system_action == expert_action).lower(),
                "wall_latency_ms": row["wall_latency_ms"],
            })

        summary[mode] = {
            "events": len(rows),
            "agreement": round(sum(agreements) / len(agreements), 4),
            "mean_latency_ms": round(statistics.mean(latencies), 2),
            "p95_latency_ms": percentile(sorted(latencies), 95),
            "action_distribution": dict(sorted(actions.items())),
        }

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(output_dir / "policy_comparison.csv", comparison_rows)
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


def percentile(sorted_values: list[int], pct: int) -> int:
    index = max(0, min(len(sorted_values) - 1, round((pct / 100) * len(sorted_values) + 0.5) - 1))
    return sorted_values[index]


def to_markdown(summary: dict) -> str:
    lines = [
        "# Policy Mode Comparison",
        "",
        "| Mode | Events | Agreement with rubric labels | Mean latency | P95 latency |",
        "|---|---:|---:|---:|---:|",
    ]
    for mode, values in summary.items():
        lines.append(
            f"| `{mode}` | {values['events']} | {values['agreement']:.2%} | "
            f"{values['mean_latency_ms']} ms | {values['p95_latency_ms']} ms |"
        )
    lines.extend([
        "",
        "## Interpretation Boundary",
        "",
        "The 100 labels are rubric-coded labels intended for reproducible comparison. They should be replaced or confirmed by independent instructor labels before making strong pedagogical claims.",
        "",
    ])
    return "\n".join(lines)


if __name__ == "__main__":
    main()
