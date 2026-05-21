from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--events-csv", required=True)
    parser.add_argument("--output-prefix", required=True)
    args = parser.parse_args()

    with Path(args.events_csv).open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    latencies = [int(row["wall_latency_ms"]) for row in rows if row["status"] == "200"]
    actions = Counter(row["action"] for row in rows if row["status"] == "200")

    fig, axes = plt.subplots(1, 2, figsize=(9.2, 3.6), dpi=160)
    fig.suptitle("SocratesAI Real pilot testing benchmark (HTTP)", fontsize=12, fontweight="bold")

    axes[0].boxplot(
        latencies,
        vert=True,
        patch_artist=True,
        boxprops={"facecolor": "#8ecae6", "edgecolor": "#1d3557"},
        medianprops={"color": "#d62828", "linewidth": 2},
        whiskerprops={"color": "#1d3557"},
        capprops={"color": "#1d3557"},
    )
    axes[0].set_title("Replay request latency")
    axes[0].set_ylabel("Latency, ms")
    axes[0].set_xticks([1], ["Rule policy"])
    axes[0].grid(axis="y", alpha=0.25)

    action_order = ["CODE_HIGHLIGHT", "CONCEPTUAL_HINT", "GUIDING_QUESTION", "NO_FEEDBACK"]
    colors = ["#457b9d", "#2a9d8f", "#e9c46a", "#adb5bd"]
    axes[1].bar(action_order, [actions.get(action, 0) for action in action_order], color=colors)
    axes[1].set_title("Policy action distribution")
    axes[1].set_ylabel("Events")
    axes[1].tick_params(axis="x", rotation=35)
    axes[1].grid(axis="y", alpha=0.25)

    fig.tight_layout(rect=[0, 0, 1, 0.92])
    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_prefix.with_suffix(".png"), bbox_inches="tight")
    fig.savefig(output_prefix.with_suffix(".pdf"), bbox_inches="tight")


if __name__ == "__main__":
    main()
