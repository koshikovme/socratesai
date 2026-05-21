from __future__ import annotations

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary-json", required=True)
    parser.add_argument("--output-prefix", required=True)
    args = parser.parse_args()

    summary = json.loads(Path(args.summary_json).read_text(encoding="utf-8"))
    modes = ["RULE", "ML", "NO_POLICY"]
    agreement = [summary[mode]["agreement"] * 100 for mode in modes]
    macro_f1 = [summary[mode]["macro_f1"] * 100 for mode in modes]

    fig, ax = plt.subplots(figsize=(7.2, 3.6), dpi=160)
    x_positions = range(len(modes))
    width = 0.36
    ax.bar([x - width / 2 for x in x_positions], agreement, width=width, label="Agreement", color="#457b9d")
    ax.bar([x + width / 2 for x in x_positions], macro_f1, width=width, label="Macro F1", color="#2a9d8f")

    ax.set_title("Policy Quality on the 12-Problem Suite", fontsize=12, fontweight="bold")
    ax.set_ylabel("Score, %")
    ax.set_xticks(list(x_positions), ["Rule", "ML", "No policy"])
    ax.set_ylim(0, 100)
    ax.grid(axis="y", alpha=0.25)
    ax.legend(frameon=False)

    for bars in ax.containers:
        ax.bar_label(bars, fmt="%.1f", padding=3, fontsize=8)

    fig.tight_layout()
    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_prefix.with_suffix(".png"), bbox_inches="tight")
    fig.savefig(output_prefix.with_suffix(".pdf"), bbox_inches="tight")


if __name__ == "__main__":
    main()
