from __future__ import annotations

import argparse
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import SGDClassifier
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
)
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.feature_extraction.text import HashingVectorizer, TfidfVectorizer

BASE_DIR = Path(__file__).resolve().parent
REPO_DIR = BASE_DIR.parent
DEFAULT_DATA_DIR = REPO_DIR / "datasets" / "Codeforces-Python-Submissions" / "data"
DEFAULT_OUTPUT_DIR = BASE_DIR / "codeforces_feedback_need"
DEFAULT_FIGURE_DIR = REPO_DIR / "EJMCA" / "ml_virtual_mentor_paper" / "figures"

VERDICT_TO_STATE = {
    "OK": "accepted",
    "WRONG_ANSWER": "semantic_debug",
    "PARTIAL": "semantic_debug",
    "FAILED": "semantic_debug",
    "REJECTED": "semantic_debug",
    "CHALLENGED": "semantic_debug",
    "SKIPPED": "semantic_debug",
    "TIME_LIMIT_EXCEEDED": "efficiency_review",
    "IDLENESS_LIMIT_EXCEEDED": "efficiency_review",
    "RUNTIME_ERROR": "execution_safety",
    "MEMORY_LIMIT_EXCEEDED": "execution_safety",
    "CRASHED": "execution_safety",
    "COMPILATION_ERROR": "syntax_repair",
}

STATE_LABELS = [
    "accepted",
    "semantic_debug",
    "execution_safety",
    "efficiency_review",
    "syntax_repair",
]

STATE_DISPLAY = {
    "accepted": "Accepted",
    "semantic_debug": "Semantic debug",
    "execution_safety": "Execution safety",
    "efficiency_review": "Efficiency review",
    "syntax_repair": "Syntax repair",
}

BASE_NUMERIC_COLUMNS = [
    "problem_rating",
    "code_chars",
    "code_lines",
    "blank_line_count",
    "comment_line_count",
    "avg_line_length",
    "max_line_length",
    "branch_count",
    "loop_count",
    "function_count",
    "class_count",
    "import_count",
    "input_count",
    "print_count",
    "try_count",
    "except_count",
]

EXECUTION_COLUMNS = [
    "passed_test_count",
    "time_consumed_ms",
    "memory_consumed_bytes",
]

CONTEXT_CATEGORICAL_COLUMNS = [
    "language_variant",
]

FEATURE_SETS = {
    "context_metrics": {
        "description": "Problem context and source-code metrics, without raw source text.",
        "use_code": False,
        "use_tags": True,
        "numeric": BASE_NUMERIC_COLUMNS,
        "categorical": CONTEXT_CATEGORICAL_COLUMNS,
    },
    "source_code": {
        "description": "Hashed source-code character n-grams only.",
        "use_code": True,
        "use_tags": False,
        "numeric": [],
        "categorical": [],
    },
    "source_context": {
        "description": "Source-code n-grams with problem context and static source metrics.",
        "use_code": True,
        "use_tags": True,
        "numeric": BASE_NUMERIC_COLUMNS,
        "categorical": CONTEXT_CATEGORICAL_COLUMNS,
    },
    "execution_enriched": {
        "description": "Source, problem context, static metrics, and run-result signals.",
        "use_code": True,
        "use_tags": True,
        "numeric": BASE_NUMERIC_COLUMNS + EXECUTION_COLUMNS,
        "categorical": CONTEXT_CATEGORICAL_COLUMNS,
    },
}


@dataclass
class SplitInfo:
    train_idx: np.ndarray
    test_idx: np.ndarray
    train_groups: list[str]
    test_groups: list[str]


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Train feedback-state models for the SocratesAI "
            "virtual mentor study."
        )
    )
    parser.add_argument("--data-dir", default=str(DEFAULT_DATA_DIR))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--figure-dir", default=str(DEFAULT_FIGURE_DIR))
    parser.add_argument("--rating-max", type=int, default=1200)
    parser.add_argument("--test-size", type=float, default=0.25)
    parser.add_argument("--random-state", type=int, default=42)
    parser.add_argument(
        "--max-rows-per-class",
        type=int,
        default=50000,
        help=(
            "Class cap used after filtering. Use 0 to train on every eligible row. "
            "The cap keeps the large classes from hiding minority feedback states."
        ),
    )
    parser.add_argument(
        "--max-code-chars",
        type=int,
        default=5000,
        help="Maximum source characters retained for vectorisation.",
    )
    parser.add_argument("--hash-features", type=int, default=65536)
    parser.add_argument(
        "--max-iter",
        type=int,
        default=60,
        help="Fixed number of SGD epochs for each linear classifier.",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    figure_dir = Path(args.figure_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    figure_dir.mkdir(parents=True, exist_ok=True)

    eligible = load_dataset(Path(args.data_dir), args.rating_max, args.max_code_chars)
    modeled = sample_rows(eligible, args.max_rows_per_class, args.random_state)
    split = make_group_split(modeled, args.test_size, args.random_state)

    results: dict[str, Any] = {}
    fitted_models: dict[str, Pipeline] = {}
    for feature_set_name in ["dummy_majority", *FEATURE_SETS.keys()]:
        print(f"Training {feature_set_name}...")
        model, metrics = train_and_evaluate(
            modeled,
            split,
            feature_set_name,
            args.hash_features,
            args.max_iter,
            args.random_state,
        )
        results[feature_set_name] = metrics
        if model is not None:
            fitted_models[feature_set_name] = model

    best_name = select_best_source_model(results)
    best_model = fitted_models[best_name]

    summary = build_summary(
        eligible=eligible,
        modeled=modeled,
        split=split,
        results=results,
        best_name=best_name,
        args=args,
    )

    results_path = output_dir / "codeforces_feedback_need_results.json"
    report_path = output_dir / "codeforces_feedback_need_report.md"
    model_path = output_dir / f"{best_name}_model.joblib"
    split_path = output_dir / "codeforces_feedback_need_split_summary.csv"
    sample_path = output_dir / "codeforces_feedback_need_modeled_rows.csv"

    results_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    report_path.write_text(build_markdown_report(summary), encoding="utf-8")
    for feature_set_name, fitted_model in fitted_models.items():
        joblib.dump(fitted_model, output_dir / f"{feature_set_name}_model.joblib", compress=3)
    joblib.dump(best_model, model_path, compress=3)
    write_split_summary(modeled, split, split_path)
    write_modeled_rows(modeled, sample_path)

    create_figures(summary, figure_dir)

    print(f"Saved results: {results_path.resolve()}")
    print(f"Saved report: {report_path.resolve()}")
    print(f"Saved models: {output_dir.resolve()}/*_model.joblib")
    print(f"Saved figures: {figure_dir.resolve()}")


def load_dataset(data_dir: Path, rating_max: int, max_code_chars: int) -> pd.DataFrame:
    if not data_dir.exists():
        raise FileNotFoundError(f"Dataset directory not found: {data_dir.resolve()}")

    files = sorted(data_dir.glob("train-*.parquet"))
    test_file = data_dir / "test-00000-of-00001.parquet"
    if test_file.exists():
        files.append(test_file)
    if not files:
        raise FileNotFoundError(f"No parquet files found in {data_dir.resolve()}")

    columns = [
        "contestId",
        "index",
        "name",
        "rating",
        "tags",
        "programmingLanguage",
        "verdict",
        "passedTestCount",
        "timeConsumedMillis",
        "memoryConsumedBytes",
        "code",
    ]

    frames = []
    row_offset = 0
    for path in files:
        frame = pd.read_parquet(path, columns=columns)
        frame["dataset_file"] = path.name
        frame["dataset_row"] = np.arange(row_offset, row_offset + len(frame))
        row_offset += len(frame)
        frames.append(frame)

    df = pd.concat(frames, ignore_index=True)
    df = df.rename(
        columns={
            "programmingLanguage": "language_variant",
            "passedTestCount": "passed_test_count",
            "timeConsumedMillis": "time_consumed_ms",
            "memoryConsumedBytes": "memory_consumed_bytes",
            "rating": "problem_rating",
        }
    )
    df["problem_id"] = df["contestId"].astype(str) + "/" + df["index"].astype(str)
    df["verdict"] = df["verdict"].astype(str).str.strip()
    df["mentor_state"] = df["verdict"].map(VERDICT_TO_STATE)
    df = df.dropna(subset=["mentor_state", "code"]).copy()
    df = df[df["problem_rating"].fillna(10_000).astype(float) <= rating_max].copy()
    df = df[df["code"].astype(str).str.strip().ne("")].copy()
    df["code"] = df["code"].astype(str)
    df["code_model_text"] = df["code"].str.slice(0, max_code_chars)
    df["tags_text"] = df["tags"].apply(format_tags)
    add_code_metrics(df)

    keep_columns = [
        "dataset_file",
        "dataset_row",
        "problem_id",
        "name",
        "tags_text",
        "language_variant",
        "verdict",
        "mentor_state",
        "code_model_text",
        *BASE_NUMERIC_COLUMNS,
        *EXECUTION_COLUMNS,
    ]
    return df[keep_columns].reset_index(drop=True)


def format_tags(value: Any) -> str:
    if isinstance(value, np.ndarray):
        return " ".join(str(item).strip().replace(" ", "_") for item in value if str(item).strip())
    if isinstance(value, list):
        return " ".join(str(item).strip().replace(" ", "_") for item in value if str(item).strip())
    if pd.isna(value):
        return ""
    text = str(value).strip()
    if not text:
        return ""
    return re.sub(r"[\[\]',\"]+", " ", text).replace(" ", "_")


def add_code_metrics(df: pd.DataFrame) -> None:
    code = df["code"].astype(str)
    df["code_chars"] = code.str.len()
    df["code_lines"] = code.str.count("\n") + 1
    df["blank_line_count"] = code.str.count(r"(?m)^\s*$")
    df["comment_line_count"] = code.str.count(r"(?m)^\s*#")
    df["avg_line_length"] = df["code_chars"] / df["code_lines"].clip(lower=1)
    df["max_line_length"] = code.apply(max_line_length)
    df["branch_count"] = code.str.count(r"\b(if|elif|else)\b")
    df["loop_count"] = code.str.count(r"\b(for|while)\b")
    df["function_count"] = code.str.count(r"\bdef\s+[A-Za-z_]\w*")
    df["class_count"] = code.str.count(r"\bclass\s+[A-Za-z_]\w*")
    df["import_count"] = code.str.count(r"\b(import|from)\b")
    df["input_count"] = code.str.count(r"\binput\s*\(")
    df["print_count"] = code.str.count(r"\bprint\s*\(")
    df["try_count"] = code.str.count(r"\btry\s*:")
    df["except_count"] = code.str.count(r"\bexcept\b")


def max_line_length(source: str) -> int:
    if not source:
        return 0
    return max((len(line) for line in source.splitlines()), default=0)


def sample_rows(df: pd.DataFrame, max_rows_per_class: int, random_state: int) -> pd.DataFrame:
    if max_rows_per_class <= 0:
        sampled = df.copy()
    else:
        pieces = []
        for label in STATE_LABELS:
            part = df[df["mentor_state"] == label]
            if len(part) > max_rows_per_class:
                part = part.sample(n=max_rows_per_class, random_state=random_state)
            pieces.append(part)
        sampled = pd.concat(pieces, ignore_index=True)
    return sampled.sample(frac=1.0, random_state=random_state).reset_index(drop=True)


def make_group_split(df: pd.DataFrame, test_size: float, random_state: int) -> SplitInfo:
    splitter = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_state)
    groups = df["problem_id"].astype(str)
    train_idx, test_idx = next(splitter.split(df, df["mentor_state"], groups))
    train_groups = sorted(set(groups.iloc[train_idx]))
    test_groups = sorted(set(groups.iloc[test_idx]))
    return SplitInfo(
        train_idx=np.array(train_idx),
        test_idx=np.array(test_idx),
        train_groups=train_groups,
        test_groups=test_groups,
    )


def train_and_evaluate(
    df: pd.DataFrame,
    split: SplitInfo,
    feature_set_name: str,
    hash_features: int,
    max_iter: int,
    random_state: int,
) -> tuple[Pipeline | None, dict[str, Any]]:
    y_train = df.iloc[split.train_idx]["mentor_state"]
    y_test = df.iloc[split.test_idx]["mentor_state"]
    x_train = df.iloc[split.train_idx]
    x_test = df.iloc[split.test_idx]

    if feature_set_name == "dummy_majority":
        dummy = DummyClassifier(strategy="most_frequent")
        dummy.fit(np.zeros((len(y_train), 1)), y_train)
        predictions = dummy.predict(np.zeros((len(y_test), 1)))
        return None, collect_metrics(y_test, predictions, STATE_LABELS)

    feature_set = FEATURE_SETS[feature_set_name]
    model = Pipeline(
        steps=[
            ("features", build_preprocessor(feature_set, hash_features)),
            (
                "classifier",
                SGDClassifier(
                    loss="log_loss",
                    penalty="l2",
                    alpha=1e-5,
                    max_iter=max_iter,
                    tol=None,
                    class_weight="balanced",
                    random_state=random_state,
                ),
            ),
        ]
    )
    model.fit(x_train, y_train)
    predictions = model.predict(x_test)
    metrics = collect_metrics(y_test, predictions, STATE_LABELS)
    metrics["description"] = feature_set["description"]
    return model, metrics


def build_preprocessor(feature_set: dict[str, Any], hash_features: int) -> ColumnTransformer:
    transformers: list[tuple[str, Any, Any]] = []

    if feature_set["use_code"]:
        transformers.append(
            (
                "code",
                HashingVectorizer(
                    analyzer="char_wb",
                    ngram_range=(3, 5),
                    n_features=hash_features,
                    alternate_sign=False,
                    lowercase=False,
                    norm="l2",
                ),
                "code_model_text",
            )
        )

    if feature_set["use_tags"]:
        transformers.append(
            (
                "tags",
                TfidfVectorizer(
                    token_pattern=r"(?u)\b[^\s]+\b",
                    lowercase=False,
                    min_df=2,
                ),
                "tags_text",
            )
        )

    numeric_columns = feature_set["numeric"]
    if numeric_columns:
        transformers.append(
            (
                "numeric",
                Pipeline(
                    steps=[
                        ("imputer", SimpleImputer(strategy="median")),
                        ("scaler", StandardScaler(with_mean=False)),
                    ]
                ),
                numeric_columns,
            )
        )

    categorical_columns = feature_set["categorical"]
    if categorical_columns:
        transformers.append(
            (
                "categorical",
                OneHotEncoder(handle_unknown="ignore"),
                categorical_columns,
            )
        )

    return ColumnTransformer(transformers=transformers, sparse_threshold=1.0)


def collect_metrics(y_true: pd.Series, y_pred: np.ndarray, labels: list[str]) -> dict[str, Any]:
    report = classification_report(
        y_true,
        y_pred,
        labels=labels,
        target_names=[STATE_DISPLAY[label] for label in labels],
        output_dict=True,
        zero_division=0,
    )
    matrix = confusion_matrix(y_true, y_pred, labels=labels)
    row_sums = matrix.sum(axis=1, keepdims=True)
    normalized = np.divide(matrix, row_sums, out=np.zeros_like(matrix, dtype=float), where=row_sums != 0)

    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "balanced_accuracy": float(balanced_accuracy_score(y_true, y_pred)),
        "macro_f1": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
        "weighted_f1": float(f1_score(y_true, y_pred, average="weighted", zero_division=0)),
        "classification_report": make_json_safe(report),
        "confusion_matrix": matrix.astype(int).tolist(),
        "confusion_matrix_normalized": normalized.tolist(),
        "prediction_distribution": count_values(pd.Series(y_pred)),
    }


def select_best_source_model(results: dict[str, Any]) -> str:
    candidates = ["source_context", "source_code", "context_metrics"]
    return max(candidates, key=lambda name: results[name]["macro_f1"])


def build_summary(
    eligible: pd.DataFrame,
    modeled: pd.DataFrame,
    split: SplitInfo,
    results: dict[str, Any],
    best_name: str,
    args: argparse.Namespace,
) -> dict[str, Any]:
    train_df = modeled.iloc[split.train_idx]
    test_df = modeled.iloc[split.test_idx]
    return {
        "study_name": "Codeforces Python feedback-state model",
        "dataset": {
            "source": "datasets/Codeforces-Python-Submissions",
            "eligible_rows_after_filtering": int(len(eligible)),
            "modeled_rows": int(len(modeled)),
            "rating_max": int(args.rating_max),
            "max_rows_per_class": int(args.max_rows_per_class),
            "max_code_chars": int(args.max_code_chars),
            "hash_features": int(args.hash_features),
            "sgd_epochs": int(args.max_iter),
            "problem_count_eligible": int(eligible["problem_id"].nunique()),
            "problem_count_modeled": int(modeled["problem_id"].nunique()),
            "class_distribution_eligible": count_values(eligible["mentor_state"]),
            "class_distribution_modeled": count_values(modeled["mentor_state"]),
            "verdict_distribution_eligible": count_values(eligible["verdict"]),
            "language_distribution_modeled": count_values(modeled["language_variant"]),
        },
        "split": {
            "strategy": "train/test split by problem identifier",
            "test_size": float(args.test_size),
            "train_rows": int(len(split.train_idx)),
            "test_rows": int(len(split.test_idx)),
            "train_problem_count": int(len(split.train_groups)),
            "test_problem_count": int(len(split.test_groups)),
            "problem_overlap": int(len(set(split.train_groups).intersection(split.test_groups))),
            "train_class_distribution": count_values(train_df["mentor_state"]),
            "test_class_distribution": count_values(test_df["mentor_state"]),
        },
        "models": results,
        "best_source_model": best_name,
        "state_labels": STATE_LABELS,
        "state_display": STATE_DISPLAY,
    }


def write_split_summary(df: pd.DataFrame, split: SplitInfo, output_path: Path) -> None:
    frame = df[["dataset_file", "dataset_row", "problem_id", "verdict", "mentor_state"]].copy()
    frame["split"] = "train"
    frame.loc[split.test_idx, "split"] = "test"
    frame.to_csv(output_path, index=False)


def write_modeled_rows(df: pd.DataFrame, output_path: Path) -> None:
    columns = [
        "dataset_file",
        "dataset_row",
        "problem_id",
        "problem_rating",
        "tags_text",
        "language_variant",
        "verdict",
        "mentor_state",
        "code_chars",
        "code_lines",
        "passed_test_count",
        "time_consumed_ms",
        "memory_consumed_bytes",
    ]
    df[columns].to_csv(output_path, index=False)


def build_markdown_report(summary: dict[str, Any]) -> str:
    lines = [
        "# Codeforces Feedback-Need Model Study",
        "",
        "This study trains ML models that map a beginner-level Python submission to a feedback state.",
        "Labels are derived from official Codeforces judge outcomes, and the split holds out complete problems.",
        "",
        "## Dataset",
        "",
        f"- Eligible rows after filtering: {summary['dataset']['eligible_rows_after_filtering']}",
        f"- Modeled rows: {summary['dataset']['modeled_rows']}",
        f"- Rating maximum: {summary['dataset']['rating_max']}",
        f"- Modeled problems: {summary['dataset']['problem_count_modeled']}",
        f"- Problem overlap between train and test: {summary['split']['problem_overlap']}",
        "",
        "## Class Distribution",
        "",
        "| Feedback state | Rows |",
        "|---|---:|",
    ]
    for label in STATE_LABELS:
        count = summary["dataset"]["class_distribution_modeled"].get(label, 0)
        lines.append(f"| {STATE_DISPLAY[label]} | {count} |")

    lines.extend(
        [
            "",
            "## Model Comparison",
            "",
            "| Model | Accuracy | Balanced accuracy | Macro F1 | Weighted F1 |",
            "|---|---:|---:|---:|---:|",
        ]
    )
    for name, metrics in summary["models"].items():
        lines.append(
            "| "
            + name
            + f" | {metrics['accuracy']:.4f}"
            + f" | {metrics['balanced_accuracy']:.4f}"
            + f" | {metrics['macro_f1']:.4f}"
            + f" | {metrics['weighted_f1']:.4f} |"
        )

    best = summary["best_source_model"]
    lines.extend(
        [
            "",
            f"Best non-execution source model: `{best}`.",
            "",
            "## Per-Class F1",
            "",
            "| Class | " + " | ".join(summary["models"].keys()) + " |",
            "|---" + "|---:" * len(summary["models"]) + "|",
        ]
    )
    for label in STATE_LABELS:
        display = STATE_DISPLAY[label]
        row = [display]
        for metrics in summary["models"].values():
            report = metrics["classification_report"]
            row.append(f"{report[display]['f1-score']:.4f}")
        lines.append("| " + " | ".join(row) + " |")

    return "\n".join(lines) + "\n"


def create_figures(summary: dict[str, Any], figure_dir: Path) -> None:
    plt.rcParams.update(
        {
            "font.family": "serif",
            "font.size": 10,
            "axes.edgecolor": "black",
            "axes.linewidth": 0.8,
        }
    )
    create_pipeline_figure(figure_dir)
    create_distribution_figure(summary, figure_dir)
    create_feature_comparison_figure(summary, figure_dir)
    create_confusion_matrix_figure(summary, figure_dir)
    create_per_class_f1_figure(summary, figure_dir)


def create_pipeline_figure(figure_dir: Path) -> None:
    fig, ax = plt.subplots(figsize=(3.9, 4.5))
    ax.axis("off")
    labels = [
        "Beginner Python\nsubmissions",
        "Feedback categories\nfrom judge results",
        "Train/test split\nby problem",
        "Code and problem\nfeatures",
        "ML feedback\nprediction",
        "Feedback action\nin SocratesAI",
    ]
    positions = [(0.50, y_pos) for y_pos in [0.90, 0.74, 0.58, 0.42, 0.26, 0.10]]
    for i, ((x_pos, y_pos), label) in enumerate(zip(positions, labels)):
        ax.text(
            x_pos,
            y_pos,
            label,
            ha="center",
            va="center",
            fontsize=9,
            bbox={
                "boxstyle": "round,pad=0.24,rounding_size=0.02",
                "facecolor": "0.94",
                "edgecolor": "0.15",
                "linewidth": 0.8,
            },
            transform=ax.transAxes,
        )
    arrow_pairs = [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5)]
    for start, end in arrow_pairs:
        x0, y0 = positions[start]
        x1, y1 = positions[end]
        xytext = (x0, y0 - 0.06)
        xy = (x1, y1 + 0.06)
        ax.annotate(
            "",
            xy=xy,
            xytext=xytext,
            arrowprops={"arrowstyle": "->", "lw": 0.8, "color": "0.15"},
            xycoords=ax.transAxes,
        )
    save_figure(fig, figure_dir, "Figure1_ml_pipeline")


def create_distribution_figure(summary: dict[str, Any], figure_dir: Path) -> None:
    counts = summary["dataset"]["class_distribution_modeled"]
    labels = [STATE_DISPLAY[label] for label in STATE_LABELS]
    values = [counts.get(label, 0) for label in STATE_LABELS]

    fig, ax = plt.subplots(figsize=(6.2, 3.4))
    ax.bar(labels, values, color=["0.25", "0.42", "0.58", "0.70", "0.82"], edgecolor="black", linewidth=0.5)
    ax.set_ylabel("Submissions")
    ax.set_title("Feedback-state distribution after beginner-level filtering")
    ax.tick_params(axis="x", rotation=25)
    ax.grid(axis="y", color="0.85", linewidth=0.6)
    save_figure(fig, figure_dir, "Figure2_state_distribution")


def create_feature_comparison_figure(summary: dict[str, Any], figure_dir: Path) -> None:
    model_order = ["dummy_majority", "context_metrics", "source_code", "source_context", "execution_enriched"]
    labels = ["Majority", "Context\nmetrics", "Source\ncode", "Source +\ncontext", "Code +\nrun results"]
    macro_f1 = [summary["models"][name]["macro_f1"] for name in model_order]
    balanced = [summary["models"][name]["balanced_accuracy"] for name in model_order]
    x = np.arange(len(labels))
    width = 0.36

    fig, ax = plt.subplots(figsize=(6.4, 3.5))
    ax.bar(x - width / 2, macro_f1, width, label="Macro F1", color="0.25", edgecolor="black", linewidth=0.5)
    ax.bar(x + width / 2, balanced, width, label="Balanced accuracy", color="0.72", edgecolor="black", linewidth=0.5)
    ax.set_ylim(0.0, 1.0)
    ax.set_ylabel("Score")
    ax.set_xticks(x, labels)
    ax.set_title("Feature-set ablation on unseen problems")
    ax.legend(frameon=False, loc="upper left")
    ax.grid(axis="y", color="0.85", linewidth=0.6)
    save_figure(fig, figure_dir, "Figure3_feature_set_comparison")


def create_confusion_matrix_figure(summary: dict[str, Any], figure_dir: Path) -> None:
    model_name = summary["best_source_model"]
    matrix = np.array(summary["models"][model_name]["confusion_matrix_normalized"])
    labels = ["Accepted", "Semantic", "Execution", "Efficiency", "Syntax"]

    fig, ax = plt.subplots(figsize=(6.1, 5.0))
    image = ax.imshow(matrix, cmap="Greys", vmin=0.0, vmax=1.0)
    ax.set_xticks(np.arange(len(labels)), labels)
    ax.set_yticks(np.arange(len(labels)), labels)
    ax.tick_params(axis="x", rotation=30)
    ax.set_xlabel("Predicted state")
    ax.set_ylabel("True state")
    ax.set_title("Normalized confusion matrix: source-code model")
    for i in range(matrix.shape[0]):
        for j in range(matrix.shape[1]):
            value = matrix[i, j]
            ax.text(
                j,
                i,
                f"{value:.2f}",
                ha="center",
                va="center",
                color="white" if value > 0.55 else "black",
                fontsize=8,
            )
    fig.colorbar(image, ax=ax, fraction=0.046, pad=0.04)
    save_figure(fig, figure_dir, "Figure4_confusion_matrix")


def create_per_class_f1_figure(summary: dict[str, Any], figure_dir: Path) -> None:
    source_name = summary["best_source_model"]
    compared = [source_name, "execution_enriched"]
    labels = [STATE_DISPLAY[label] for label in STATE_LABELS]
    y = np.arange(len(labels))
    width = 0.36

    fig, ax = plt.subplots(figsize=(6.2, 4.1))
    for offset, name, color in [(-width / 2, compared[0], "0.25"), (width / 2, compared[1], "0.72")]:
        report = summary["models"][name]["classification_report"]
        values = [report[STATE_DISPLAY[label]]["f1-score"] for label in STATE_LABELS]
        ax.barh(y + offset, values, width, label=name.replace("_", " "), color=color, edgecolor="black", linewidth=0.5)
    ax.set_xlim(0.0, 1.0)
    ax.set_xlabel("F1-score")
    ax.set_yticks(y, labels)
    ax.invert_yaxis()
    ax.set_title("Per-class F1 for source-code and code-and-run models")
    handles, _ = ax.get_legend_handles_labels()
    ax.legend(handles, ["source code", "code + run results"], frameon=False, loc="lower center", bbox_to_anchor=(0.5, -0.26), ncol=2)
    ax.grid(axis="x", color="0.85", linewidth=0.6)
    fig.subplots_adjust(bottom=0.23)
    save_figure(fig, figure_dir, "Figure5_per_class_f1")


def save_figure(fig: plt.Figure, figure_dir: Path, stem: str) -> None:
    fig.tight_layout()
    for suffix in ["pdf", "eps", "png"]:
        fig.savefig(figure_dir / f"{stem}.{suffix}", dpi=600, bbox_inches="tight")
    plt.close(fig)


def count_values(series: pd.Series) -> dict[str, int]:
    counts = series.astype(str).value_counts(dropna=False)
    return {str(label): int(count) for label, count in counts.items()}


def make_json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(k): make_json_safe(v) for k, v in value.items()}
    if isinstance(value, list):
        return [make_json_safe(v) for v in value]
    if isinstance(value, np.integer):
        return int(value)
    if isinstance(value, np.floating):
        if math.isnan(float(value)):
            return None
        return float(value)
    if isinstance(value, np.ndarray):
        return value.tolist()
    return value


if __name__ == "__main__":
    main()
