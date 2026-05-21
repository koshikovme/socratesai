from __future__ import annotations

import os
from pathlib import Path

import joblib
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = Path(os.getenv("POLICY_MODEL_PATH", str(BASE_DIR / "mentor_policy_model.joblib")))
if not MODEL_PATH.is_absolute():
    cwd_path = Path.cwd() / MODEL_PATH
    MODEL_PATH = cwd_path if cwd_path.exists() else BASE_DIR / MODEL_PATH


def main() -> None:
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"Model file not found: {MODEL_PATH.resolve()}")

    sample = pd.DataFrame(
        [
            {
                "error_type": "OFF_BY_ONE",
                "severity": "MEDIUM",
                "compile_success": True,
                "tests_passed": 1,
                "tests_failed": 2,
                "same_error_count": 2,
                "total_errors_seen": 4,
                "attempt_no": 2,
                "last_feedback_action": "CODE_HIGHLIGHT",
                "last_feedback_success": False,
                "has_suspicious_region": True,
                "code_lines": 18,
                "total_feedback_count_in_session": 3,
            }
        ]
    )

    model = joblib.load(MODEL_PATH)
    prediction = model.predict(sample)[0]
    print(f"Predicted action: {prediction}")


if __name__ == "__main__":
    main()
