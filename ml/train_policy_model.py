from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.model_selection import GroupShuffleSplit, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DATA_PATH = BASE_DIR / "policy_dataset.csv"
DEFAULT_OUTPUT_PREFIX = BASE_DIR / "mentor_policy_model"

FEATURE_COLUMNS = [
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
]

TARGET_CANDIDATES = ["target_feedback_action", "feedback_action"]
CATEGORICAL_COLUMNS = [
    "error_type",
    "severity",
    "last_feedback_action",
    "last_feedback_success",
]
BOOLEAN_COLUMNS = [
    "compile_success",
    "has_suspicious_region",
]
NUMERIC_COLUMNS = [
    "tests_passed",
    "tests_failed",
    "same_error_count",
    "total_errors_seen",
    "attempt_no",
    "code_lines",
    "total_feedback_count_in_session",
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the SocratesAI mentor policy classifier.")
    parser.add_argument(
        "--input",
        default=str(DEFAULT_DATA_PATH),
        help="Training CSV. Defaults to ml/policy_dataset.csv.",
    )
    parser.add_argument(
        "--output-prefix",
        default=str(DEFAULT_OUTPUT_PREFIX),
        help="Output prefix for model, metadata, metrics, report, and figure files.",
    )
    parser.add_argument(
        "--target-column",
        choices=TARGET_CANDIDATES,
        default=None,
        help="Target column. Defaults to target_feedback_action when present, otherwise feedback_action.",
    )
    parser.add_argument(
        "--group-column",
        default=None,
        help="Optional group holdout column, for example student_id or problem_slug.",
    )
    parser.add_argument(
        "--test-size",
        type=float,
        default=0.2,
        help="Holdout fraction.",
    )
    parser.add_argument(
        "--random-state",
        type=int,
        default=42,
        help="Random seed.",
    )
    args = parser.parse_args()

    data_path = Path(args.input)
    output_prefix = Path(args.output_prefix)
    df, target_column = load_dataset(data_path, args.target_column)
    print(f"Loaded dataset rows: {len(df)}")
    print(f"Using target column: {target_column}")

    split = split_dataset(
        df,
        target_column,
        group_column=args.group_column,
        test_size=args.test_size,
        random_state=args.random_state,
    )
    print(f"Split strategy: {split['strategy']}")
    print(f"Train rows: {len(split['x_train'])}")
    print(f"Test rows: {len(split['x_test'])}")

    pipeline = build_pipeline(args.random_state)
    pipeline.fit(split["x_train"], split["y_train"])

    predictions = pipeline.predict(split["x_test"])
    labels = sorted(df[target_column].dropna().unique().tolist())
    accuracy = float(accuracy_score(split["y_test"], predictions))
    macro_f1 = float(f1_score(split["y_test"], predictions, labels=labels, average="macro", zero_division=0))
    report = classification_report(
        split["y_test"],
        predictions,
        labels=labels,
        zero_division=0,
        output_dict=True,
    )
    matrix = confusion_matrix(split["y_test"], predictions, labels=labels)

    print("\n=== Metrics ===")
    print(f"Accuracy: {accuracy:.4f}")
    print(f"Macro F1: {macro_f1:.4f}")

    print("\n=== Classification Report ===")
    print(classification_report(split["y_test"], predictions, labels=labels, zero_division=0))

    print("\n=== Confusion Matrix ===")
    print(matrix)

    model_path = output_prefix.with_suffix(".joblib")
    metadata_path = output_prefix.with_name(f"{output_prefix.name}_metadata.json")
    metrics_path = output_prefix.with_name(f"{output_prefix.name}_metrics.json")
    report_path = output_prefix.with_name(f"{output_prefix.name}_report.md")
    figure_path = output_prefix.with_name(f"{output_prefix.name}_confusion_matrix.png")
    output_prefix.parent.mkdir(parents=True, exist_ok=True)

    joblib.dump(pipeline, model_path)
    metadata = {
        "feature_columns": FEATURE_COLUMNS,
        "target_column": target_column,
        "labels": labels,
        "model_path": str(model_path),
    }
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    metrics = {
        "dataset_path": str(data_path),
        "dataset_rows": int(len(df)),
        "train_rows": int(len(split["x_train"])),
        "test_rows": int(len(split["x_test"])),
        "target_column": target_column,
        "split_strategy": split["strategy"],
        "group_column": split["group_column"],
        "train_groups": split["train_groups"],
        "test_groups": split["test_groups"],
        "target_distribution": count_values(df[target_column]),
        "train_target_distribution": count_values(split["y_train"]),
        "test_target_distribution": count_values(split["y_test"]),
        "accuracy": accuracy,
        "macro_f1": macro_f1,
        "labels": labels,
        "classification_report": report,
        "confusion_matrix": matrix.tolist(),
        "feature_columns": FEATURE_COLUMNS,
        "model": {
            "type": "RandomForestClassifier",
            "n_estimators": 200,
            "max_depth": 8,
            "min_samples_leaf": 2,
            "class_weight": "balanced_subsample",
            "random_state": args.random_state,
        },
    }
    metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    report_path.write_text(build_markdown_report(metrics), encoding="utf-8")
    write_confusion_matrix_figure(matrix, labels, figure_path)

    print(f"\nSaved model to: {model_path.resolve()}")
    print(f"Saved metadata to: {metadata_path.resolve()}")
    print(f"Saved metrics to: {metrics_path.resolve()}")
    print(f"Saved report to: {report_path.resolve()}")
    print(f"Saved figure to: {figure_path.resolve()}")


def load_dataset(path: Path, requested_target_column: str | None = None) -> tuple[pd.DataFrame, str]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path.resolve()}")

    df = pd.read_csv(path)
    target_column = select_target_column(df, requested_target_column)

    missing = [column for column in FEATURE_COLUMNS if column not in df.columns]
    if missing:
        raise ValueError(f"Dataset is missing required feature columns: {missing}")

    df = normalize_dataset(df)
    df[target_column] = df[target_column].replace("", pd.NA)
    df = df.dropna(subset=[target_column]).reset_index(drop=True)
    if df.empty:
        raise ValueError(f"Dataset has no non-empty labels in target column: {target_column}")

    return df, target_column


def select_target_column(df: pd.DataFrame, requested_target_column: str | None) -> str:
    if requested_target_column:
        if requested_target_column not in df.columns:
            raise ValueError(f"Requested target column is missing: {requested_target_column}")
        return requested_target_column

    target_column = next((column for column in TARGET_CANDIDATES if column in df.columns), None)
    if target_column is None:
        raise ValueError(
            f"Dataset must include one of target columns: {', '.join(TARGET_CANDIDATES)}"
        )
    return target_column


def normalize_dataset(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()

    for column in BOOLEAN_COLUMNS + ["last_feedback_success"]:
        if column in df.columns:
            df[column] = (
                df[column]
                .astype(str)
                .str.strip()
                .str.lower()
                .map({
                    "true": True,
                    "false": False,
                    "1": True,
                    "0": False,
                    "nan": None,
                    "none": None,
                    "": None,
                })
            )

    for column in CATEGORICAL_COLUMNS:
        if column in df.columns:
            df[column] = df[column].replace("", pd.NA)

    return df


def build_pipeline(random_state: int) -> Pipeline:
    categorical_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="constant", fill_value="MISSING")),
            ("one_hot", OneHotEncoder(handle_unknown="ignore")),
        ]
    )
    boolean_pipeline = Pipeline(
        steps=[("imputer", SimpleImputer(strategy="most_frequent"))]
    )
    numeric_pipeline = Pipeline(
        steps=[("imputer", SimpleImputer(strategy="median"))]
    )

    preprocessor = ColumnTransformer(
        transformers=[
            ("categorical", categorical_pipeline, CATEGORICAL_COLUMNS),
            ("boolean", boolean_pipeline, BOOLEAN_COLUMNS),
            ("numeric", numeric_pipeline, NUMERIC_COLUMNS),
        ]
    )

    classifier = RandomForestClassifier(
        n_estimators=200,
        max_depth=8,
        min_samples_leaf=2,
        class_weight="balanced_subsample",
        random_state=random_state,
    )

    return Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("classifier", classifier),
        ]
    )


def split_dataset(
    df: pd.DataFrame,
    target_column: str,
    group_column: str | None,
    test_size: float,
    random_state: int,
) -> dict:
    x = df[FEATURE_COLUMNS]
    y = df[target_column]

    if group_column:
        if group_column not in df.columns:
            raise ValueError(f"Group column is missing: {group_column}")
        if df[group_column].nunique() < 2:
            raise ValueError(f"Group column must contain at least two values: {group_column}")

        splitter = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_state)
        train_idx, test_idx = next(splitter.split(x, y, groups=df[group_column]))
        train_groups = sorted(df.iloc[train_idx][group_column].dropna().astype(str).unique().tolist())
        test_groups = sorted(df.iloc[test_idx][group_column].dropna().astype(str).unique().tolist())
        return {
            "strategy": "group_holdout",
            "group_column": group_column,
            "train_groups": train_groups,
            "test_groups": test_groups,
            "x_train": x.iloc[train_idx],
            "x_test": x.iloc[test_idx],
            "y_train": y.iloc[train_idx],
            "y_test": y.iloc[test_idx],
        }

    if "student_id" in df.columns and df["student_id"].nunique() > 1:
        splitter = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_state)
        train_idx, test_idx = next(splitter.split(x, y, groups=df["student_id"]))
        return {
            "strategy": "student_holdout",
            "group_column": "student_id",
            "train_groups": sorted(df.iloc[train_idx]["student_id"].dropna().astype(str).unique().tolist()),
            "test_groups": sorted(df.iloc[test_idx]["student_id"].dropna().astype(str).unique().tolist()),
            "x_train": x.iloc[train_idx],
            "x_test": x.iloc[test_idx],
            "y_train": y.iloc[train_idx],
            "y_test": y.iloc[test_idx],
        }

    x_train, x_test, y_train, y_test = train_test_split(
        x,
        y,
        test_size=test_size,
        random_state=random_state,
        stratify=y if y.nunique() > 1 else None,
    )
    return {
        "strategy": "stratified_random" if y.nunique() > 1 else "random",
        "group_column": None,
        "train_groups": [],
        "test_groups": [],
        "x_train": x_train,
        "x_test": x_test,
        "y_train": y_train,
        "y_test": y_test,
    }


def build_markdown_report(metrics: dict) -> str:
    lines = [
        "# Mentor Policy Model Metrics",
        "",
        f"- Dataset: `{metrics['dataset_path']}`",
        f"- Dataset rows: {metrics['dataset_rows']}",
        f"- Train rows: {metrics['train_rows']}",
        f"- Test rows: {metrics['test_rows']}",
        f"- Target column: `{metrics['target_column']}`",
        f"- Split strategy: `{metrics['split_strategy']}`",
        f"- Group column: `{metrics['group_column']}`",
        f"- Accuracy: {metrics['accuracy']:.4f}",
        f"- Macro F1: {metrics['macro_f1']:.4f}",
        "",
        "## Target Distribution",
        "",
        "| Label | Dataset | Train | Test |",
        "|---|---:|---:|---:|",
    ]

    for label in metrics["labels"]:
        lines.append(
            f"| `{label}` | {metrics['target_distribution'].get(label, 0)} | "
            f"{metrics['train_target_distribution'].get(label, 0)} | "
            f"{metrics['test_target_distribution'].get(label, 0)} |"
        )

    lines.extend([
        "",
        "## Per-Class Metrics",
        "",
        "| Label | Precision | Recall | F1 | Support |",
        "|---|---:|---:|---:|---:|",
    ])

    report = metrics["classification_report"]
    for label in metrics["labels"]:
        label_metrics = report[label]
        lines.append(
            f"| `{label}` | {label_metrics['precision']:.4f} | "
            f"{label_metrics['recall']:.4f} | {label_metrics['f1-score']:.4f} | "
            f"{int(label_metrics['support'])} |"
        )

    lines.extend([
        "",
        "## Confusion Matrix",
        "",
        "Rows are true labels; columns are predicted labels.",
        "",
        "| True \\ Predicted | " + " | ".join(f"`{label}`" for label in metrics["labels"]) + " |",
        "|---" + "|---:" * len(metrics["labels"]) + "|",
    ])

    for label, row in zip(metrics["labels"], metrics["confusion_matrix"]):
        lines.append("| `" + label + "` | " + " | ".join(str(value) for value in row) + " |")

    if metrics["test_groups"]:
        lines.extend([
            "",
            "## Held-Out Groups",
            "",
            f"- Train groups: {', '.join(metrics['train_groups'])}",
            f"- Test groups: {', '.join(metrics['test_groups'])}",
        ])

    lines.extend([
        "",
        "## Interpretation Boundary",
        "",
        "These metrics evaluate action-label prediction for the policy selector. "
        "If the target is `feedback_action`, the model is imitating the current rule policy. "
        "If the target is `target_feedback_action`, the model is evaluated against reviewed or rubric labels. "
        "Neither setting by itself proves classroom learning gain.",
        "",
    ])
    return "\n".join(lines)


def write_confusion_matrix_figure(matrix, labels: list[str], path: Path) -> None:
    try:
        import matplotlib.pyplot as plt
    except ImportError:
        return

    fig, ax = plt.subplots(figsize=(7.2, 5.6))
    image = ax.imshow(matrix, cmap="Blues")
    ax.set_title("Policy Model Confusion Matrix")
    ax.set_xlabel("Predicted action")
    ax.set_ylabel("Target action")
    ax.set_xticks(range(len(labels)), labels, rotation=35, ha="right")
    ax.set_yticks(range(len(labels)), labels)

    max_value = matrix.max() if matrix.size else 0
    threshold = max_value / 2 if max_value else 0
    for row_index in range(len(labels)):
        for col_index in range(len(labels)):
            value = int(matrix[row_index, col_index])
            color = "white" if value > threshold else "black"
            ax.text(col_index, row_index, str(value), ha="center", va="center", color=color)

    fig.colorbar(image, ax=ax, fraction=0.046, pad=0.04)
    fig.tight_layout()
    fig.savefig(path, dpi=180)
    plt.close(fig)


def count_values(series: pd.Series) -> dict[str, int]:
    counts = series.astype(str).value_counts().sort_index()
    return {str(label): int(count) for label, count in counts.items()}


if __name__ == "__main__":
    main()
