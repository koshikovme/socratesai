from __future__ import annotations

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

DATA_PATH = Path("policy_dataset.csv")
MODEL_PATH = Path("mentor_policy_model.joblib")
METADATA_PATH = Path("mentor_policy_model_metadata.json")

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


def load_dataset(path: Path) -> tuple[pd.DataFrame, str]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path.resolve()}")

    df = pd.read_csv(path)

    target_column = next((column for column in TARGET_CANDIDATES if column in df.columns), None)
    if target_column is None:
        raise ValueError(
            f"Dataset must include one of target columns: {', '.join(TARGET_CANDIDATES)}"
        )

    missing = [column for column in FEATURE_COLUMNS if column not in df.columns]
    if missing:
        raise ValueError(f"Dataset is missing required feature columns: {missing}")

    return normalize_dataset(df), target_column


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


def build_pipeline() -> Pipeline:
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
        random_state=42,
    )

    return Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("classifier", classifier),
        ]
    )


def split_dataset(df: pd.DataFrame, target_column: str):
    x = df[FEATURE_COLUMNS]
    y = df[target_column]

    if "student_id" in df.columns and df["student_id"].nunique() > 1:
        splitter = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
        train_idx, test_idx = next(splitter.split(x, y, groups=df["student_id"]))
        return x.iloc[train_idx], x.iloc[test_idx], y.iloc[train_idx], y.iloc[test_idx]

    return train_test_split(
        x,
        y,
        test_size=0.2,
        random_state=42,
        stratify=y if y.nunique() > 1 else None,
    )


def main() -> None:
    df, target_column = load_dataset(DATA_PATH)
    print(f"Loaded dataset rows: {len(df)}")
    print(f"Using target column: {target_column}")

    x_train, x_test, y_train, y_test = split_dataset(df, target_column)
    print(f"Train rows: {len(x_train)}")
    print(f"Test rows: {len(x_test)}")

    pipeline = build_pipeline()
    pipeline.fit(x_train, y_train)

    predictions = pipeline.predict(x_test)
    accuracy = accuracy_score(y_test, predictions)
    macro_f1 = f1_score(y_test, predictions, average="macro")

    print("\n=== Metrics ===")
    print(f"Accuracy: {accuracy:.4f}")
    print(f"Macro F1: {macro_f1:.4f}")

    print("\n=== Classification Report ===")
    print(classification_report(y_test, predictions, zero_division=0))

    print("\n=== Confusion Matrix ===")
    print(confusion_matrix(y_test, predictions))

    joblib.dump(pipeline, MODEL_PATH)
    metadata = {
        "feature_columns": FEATURE_COLUMNS,
        "target_column": target_column,
        "labels": sorted(df[target_column].dropna().unique().tolist()),
    }
    METADATA_PATH.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    print(f"\nSaved model to: {MODEL_PATH.resolve()}")
    print(f"Saved metadata to: {METADATA_PATH.resolve()}")


if __name__ == "__main__":
    main()
