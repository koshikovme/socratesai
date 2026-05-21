from __future__ import annotations

import argparse
import importlib.util
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import GroupShuffleSplit, train_test_split
from sklearn.neural_network import MLPClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, OneHotEncoder, StandardScaler

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DATA_PATH = BASE_DIR / "problem_suite_policy_dataset.csv"
DEFAULT_OUTPUT_PREFIX = BASE_DIR / "policy_model_study"

TARGET_CANDIDATES = ["target_feedback_action", "feedback_action"]

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
    "suspicious_region",
    "code_lines",
    "total_feedback_count_in_session",
    "analysis_time_ms",
]

FEATURE_GROUPS = {
    "history_features": [
        "same_error_count",
        "total_errors_seen",
        "attempt_no",
        "last_feedback_success",
        "total_feedback_count_in_session",
    ],
    "analyzer_features": [
        "error_type",
        "severity",
        "compile_success",
        "tests_passed",
        "tests_failed",
        "code_lines",
        "analysis_time_ms",
    ],
    "suspicious_region": [
        "has_suspicious_region",
        "suspicious_region",
    ],
    "last_feedback_action": [
        "last_feedback_action",
    ],
}

CATEGORICAL_COLUMNS = [
    "error_type",
    "severity",
    "last_feedback_action",
    "suspicious_region",
]
BOOLEAN_COLUMNS = [
    "compile_success",
    "last_feedback_success",
    "has_suspicious_region",
]


@dataclass
class Split:
    strategy: str
    group_column: str | None
    train_groups: list[str]
    test_groups: list[str]
    train_idx: Any
    test_idx: Any


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run reproducible policy model comparison and ablation studies."
    )
    parser.add_argument("--input", default=str(DEFAULT_DATA_PATH), help="Policy dataset CSV.")
    parser.add_argument(
        "--output-prefix",
        default=str(DEFAULT_OUTPUT_PREFIX),
        help="Output prefix for JSON and Markdown study reports.",
    )
    parser.add_argument(
        "--target-column",
        choices=TARGET_CANDIDATES,
        default=None,
        help="Target column. Defaults to target_feedback_action when present.",
    )
    parser.add_argument(
        "--group-column",
        default=None,
        help="Optional holdout group column. Defaults to problem_slug, then student_id.",
    )
    parser.add_argument("--test-size", type=float, default=0.25)
    parser.add_argument("--random-state", type=int, default=42)
    args = parser.parse_args()

    data_path = Path(args.input)
    output_prefix = Path(args.output_prefix)
    df, target_column, feature_columns = load_dataset(data_path, args.target_column)
    split = split_dataset(df, target_column, args.group_column, args.test_size, args.random_state)

    study = run_study(df, target_column, feature_columns, split, args.random_state)
    study.update(
        {
            "dataset_path": str(data_path),
            "dataset_rows": int(len(df)),
            "target_column": target_column,
            "feature_columns": feature_columns,
            "target_distribution": count_values(df[target_column]),
            "split": {
                "strategy": split.strategy,
                "group_column": split.group_column,
                "train_rows": int(len(split.train_idx)),
                "test_rows": int(len(split.test_idx)),
                "train_groups": split.train_groups,
                "test_groups": split.test_groups,
            },
        }
    )

    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    json_path = output_prefix.with_name(f"{output_prefix.name}_results.json")
    report_path = output_prefix.with_name(f"{output_prefix.name}_report.md")
    json_path.write_text(json.dumps(study, indent=2), encoding="utf-8")
    report_path.write_text(build_markdown_report(study), encoding="utf-8")

    print(f"Saved study JSON: {json_path.resolve()}")
    print(f"Saved study report: {report_path.resolve()}")


def load_dataset(path: Path, requested_target_column: str | None) -> tuple[pd.DataFrame, str, list[str]]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path.resolve()}")

    df = pd.read_csv(path)
    target_column = select_target_column(df, requested_target_column)
    df = normalize_dataset(df)
    df[target_column] = df[target_column].replace("", pd.NA)
    df = df.dropna(subset=[target_column]).reset_index(drop=True)

    feature_columns = [column for column in FEATURE_COLUMNS if column in df.columns]
    if not feature_columns:
        raise ValueError("Dataset does not contain any supported policy feature columns.")
    if df.empty:
        raise ValueError(f"Dataset has no non-empty labels in target column: {target_column}")
    return df, target_column, feature_columns


def select_target_column(df: pd.DataFrame, requested_target_column: str | None) -> str:
    if requested_target_column:
        if requested_target_column not in df.columns:
            raise ValueError(f"Requested target column is missing: {requested_target_column}")
        return requested_target_column

    target_column = next((column for column in TARGET_CANDIDATES if column in df.columns), None)
    if target_column is None:
        raise ValueError(f"Dataset must include one of: {', '.join(TARGET_CANDIDATES)}")
    return target_column


def normalize_dataset(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    for column in BOOLEAN_COLUMNS:
        if column in df.columns:
            df[column] = (
                df[column]
                .astype(str)
                .str.strip()
                .str.lower()
                .map(
                    {
                        "true": 1.0,
                        "false": 0.0,
                        "1": 1.0,
                        "0": 0.0,
                        "nan": np.nan,
                        "none": np.nan,
                        "": np.nan,
                    }
                )
            )
    return df


def split_dataset(
    df: pd.DataFrame,
    target_column: str,
    requested_group_column: str | None,
    test_size: float,
    random_state: int,
) -> Split:
    group_column = requested_group_column
    if group_column is None:
        if "problem_slug" in df.columns and df["problem_slug"].nunique() > 1:
            group_column = "problem_slug"
        elif "student_id" in df.columns and df["student_id"].nunique() > 1:
            group_column = "student_id"

    x = df.drop(columns=[target_column])
    y = df[target_column]

    if group_column:
        if group_column not in df.columns:
            raise ValueError(f"Group column is missing: {group_column}")
        if df[group_column].nunique() < 2:
            raise ValueError(f"Group column must contain at least two groups: {group_column}")

        splitter = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_state)
        train_idx, test_idx = next(splitter.split(x, y, groups=df[group_column]))
        return Split(
            strategy="group_holdout",
            group_column=group_column,
            train_groups=sorted(df.iloc[train_idx][group_column].dropna().astype(str).unique().tolist()),
            test_groups=sorted(df.iloc[test_idx][group_column].dropna().astype(str).unique().tolist()),
            train_idx=train_idx,
            test_idx=test_idx,
        )

    train_idx, test_idx = train_test_split(
        df.index.to_numpy(),
        test_size=test_size,
        random_state=random_state,
        stratify=y if y.nunique() > 1 else None,
    )
    return Split(
        strategy="stratified_random" if y.nunique() > 1 else "random",
        group_column=None,
        train_groups=[],
        test_groups=[],
        train_idx=train_idx,
        test_idx=test_idx,
    )


def run_study(
    df: pd.DataFrame,
    target_column: str,
    feature_columns: list[str],
    split: Split,
    random_state: int,
) -> dict:
    experiments = build_feature_sets(feature_columns)
    model_factories = build_model_factories(random_state)

    y_train = df.iloc[split.train_idx][target_column]
    y_test = df.iloc[split.test_idx][target_column]
    labels = sorted(df[target_column].dropna().astype(str).unique().tolist())
    results = []

    if "feedback_action" in df.columns:
        predictions = df.iloc[split.test_idx]["feedback_action"].astype(str)
        results.append(
            evaluate_predictions(
                model_name="rule_baseline",
                feature_set="all_features",
                y_test=y_test,
                predictions=predictions,
                labels=labels,
                skipped_reason=None,
            )
        )

    skipped_models = []
    for model_name, factory in model_factories.items():
        for feature_set_name, columns in experiments.items():
            if not columns:
                results.append(
                    skipped_result(
                        model_name,
                        feature_set_name,
                        "feature set has no columns after ablation",
                    )
                )
                continue

            x_train = df.iloc[split.train_idx][columns]
            x_test = df.iloc[split.test_idx][columns]
            try:
                pipeline = build_pipeline(columns, factory())
                pipeline.fit(x_train, y_train)
                predictions = pipeline.predict(x_test)
                results.append(
                    evaluate_predictions(
                        model_name=model_name,
                        feature_set=feature_set_name,
                        y_test=y_test,
                        predictions=predictions,
                        labels=labels,
                        skipped_reason=None,
                    )
                )
            except Exception as exc:
                results.append(skipped_result(model_name, feature_set_name, str(exc)))

    for optional_name, package_name in [
        ("xgboost", "xgboost"),
        ("lightgbm", "lightgbm"),
    ]:
        if importlib.util.find_spec(package_name) is None:
            skipped_models.append(
                {
                    "model": optional_name,
                    "reason": f"Python package '{package_name}' is not installed.",
                }
            )

    return {
        "labels": labels,
        "results": results,
        "skipped_optional_models": skipped_models,
    }


def build_feature_sets(feature_columns: list[str]) -> dict[str, list[str]]:
    feature_sets = {"all_features": feature_columns}
    for group_name, group_columns in FEATURE_GROUPS.items():
        removed = set(group_columns)
        feature_sets[f"without_{group_name}"] = [
            column for column in feature_columns if column not in removed
        ]
    return feature_sets


def build_model_factories(random_state: int):
    return {
        "logistic_regression": lambda: LogisticRegression(
            max_iter=2000,
            class_weight="balanced",
            random_state=random_state,
        ),
        "random_forest": lambda: RandomForestClassifier(
            n_estimators=300,
            max_depth=10,
            min_samples_leaf=2,
            class_weight="balanced_subsample",
            random_state=random_state,
        ),
        "small_neural_classifier": lambda: MLPClassifier(
            hidden_layer_sizes=(32,),
            activation="relu",
            alpha=0.001,
            max_iter=1000,
            early_stopping=False,
            random_state=random_state,
        ),
    }


def build_pipeline(feature_columns: list[str], classifier) -> Pipeline:
    categorical_columns = [column for column in CATEGORICAL_COLUMNS if column in feature_columns]
    boolean_columns: list[str] = []
    numeric_columns = [
        column
        for column in feature_columns
        if column not in categorical_columns
    ]

    transformers = []
    if categorical_columns:
        transformers.append(
            (
                "categorical",
                Pipeline(
                    [
                        ("imputer", SimpleImputer(strategy="constant", fill_value="MISSING")),
                        ("one_hot", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
                    ]
                ),
                categorical_columns,
            )
        )
    if boolean_columns:
        transformers.append(
            (
                "boolean",
                Pipeline([("imputer", SimpleImputer(strategy="most_frequent"))]),
                boolean_columns,
            )
        )
    if numeric_columns:
        transformers.append(
            (
                "numeric",
                Pipeline(
                    [
                        ("imputer", SimpleImputer(strategy="median")),
                        ("scaler", StandardScaler()),
                    ]
                ),
                numeric_columns,
            )
        )

    return Pipeline(
        [
            ("preprocessor", ColumnTransformer(transformers=transformers)),
            ("to_float", FunctionTransformer(lambda x: x.astype(float))),
            ("classifier", classifier),
        ]
    )


def evaluate_predictions(
    model_name: str,
    feature_set: str,
    y_test: pd.Series,
    predictions,
    labels: list[str],
    skipped_reason: str | None,
) -> dict:
    if skipped_reason:
        return skipped_result(model_name, feature_set, skipped_reason)

    accuracy = float(accuracy_score(y_test, predictions))
    macro_f1 = float(f1_score(y_test, predictions, labels=labels, average="macro", zero_division=0))
    report = classification_report(
        y_test,
        predictions,
        labels=labels,
        output_dict=True,
        zero_division=0,
    )
    return {
        "model": model_name,
        "feature_set": feature_set,
        "status": "ok",
        "accuracy": round4(accuracy),
        "macro_f1": round4(macro_f1),
        "classification_report": report,
    }


def skipped_result(model_name: str, feature_set: str, reason: str) -> dict:
    return {
        "model": model_name,
        "feature_set": feature_set,
        "status": "skipped",
        "reason": reason,
        "accuracy": None,
        "macro_f1": None,
    }


def build_markdown_report(study: dict) -> str:
    lines = [
        "# Policy Model Comparison and Ablation Study",
        "",
        f"- Dataset: `{study['dataset_path']}`",
        f"- Rows: {study['dataset_rows']}",
        f"- Target column: `{study['target_column']}`",
        f"- Split: `{study['split']['strategy']}`",
        f"- Group column: `{study['split']['group_column']}`",
        f"- Train rows: {study['split']['train_rows']}",
        f"- Test rows: {study['split']['test_rows']}",
        "",
        "## Target Distribution",
        "",
        "| Label | Count |",
        "|---|---:|",
    ]
    for label, count in study["target_distribution"].items():
        lines.append(f"| `{label}` | {count} |")

    lines.extend(
        [
            "",
            "## Model Results",
            "",
            "| Model | Feature Set | Status | Accuracy | Macro F1 | Notes |",
            "|---|---|---|---:|---:|---|",
        ]
    )
    sorted_results = sorted(
        study["results"],
        key=lambda row: (
            row["status"] != "ok",
            -(row["macro_f1"] or -1),
            row["model"],
            row["feature_set"],
        ),
    )
    for row in sorted_results:
        accuracy = "" if row["accuracy"] is None else f"{row['accuracy']:.4f}"
        macro_f1 = "" if row["macro_f1"] is None else f"{row['macro_f1']:.4f}"
        notes = row.get("reason", "")
        lines.append(
            f"| `{row['model']}` | `{row['feature_set']}` | {row['status']} | "
            f"{accuracy} | {macro_f1} | {notes} |"
        )

    if study["skipped_optional_models"]:
        lines.extend(["", "## Optional Models", ""])
        for item in study["skipped_optional_models"]:
            lines.append(f"- `{item['model']}` skipped: {item['reason']}")

    if study["split"]["test_groups"]:
        lines.extend(
            [
                "",
                "## Held-Out Groups",
                "",
                f"- Train groups: {', '.join(study['split']['train_groups'])}",
                f"- Test groups: {', '.join(study['split']['test_groups'])}",
            ]
        )

    lines.extend(
        [
            "",
            "## Interpretation Boundary",
            "",
            "This study measures how well each model predicts the selected target action. "
            "With rubric labels it validates policy selection logic; with manually reviewed labels it becomes an expert-label benchmark. "
            "Learning gain still requires outcome labels and classroom/pilot data.",
            "",
        ]
    )
    return "\n".join(lines)


def count_values(series: pd.Series) -> dict[str, int]:
    counts = series.astype(str).value_counts().sort_index()
    return {str(label): int(count) for label, count in counts.items()}


def round4(value: float) -> float:
    return round(float(value), 4)


if __name__ == "__main__":
    main()
