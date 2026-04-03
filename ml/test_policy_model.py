from __future__ import annotations

from pathlib import Path

import joblib
import pandas as pd

MODEL_PATH = Path("mentor_policy_model.joblib")


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
