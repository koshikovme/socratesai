from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any

import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = Path(os.getenv("POLICY_MODEL_PATH", str(BASE_DIR / "mentor_policy_model.joblib")))
MENTOR_STATE_MODEL_PATH = Path(
    os.getenv(
        "MENTOR_STATE_MODEL_PATH",
        str(BASE_DIR / "codeforces_feedback_need" / "source_code_model.joblib"),
    )
)
if not MODEL_PATH.is_absolute():
    cwd_path = Path.cwd() / MODEL_PATH
    MODEL_PATH = cwd_path if cwd_path.exists() else BASE_DIR / MODEL_PATH
if not MENTOR_STATE_MODEL_PATH.is_absolute():
    cwd_path = Path.cwd() / MENTOR_STATE_MODEL_PATH
    MENTOR_STATE_MODEL_PATH = cwd_path if cwd_path.exists() else BASE_DIR / MENTOR_STATE_MODEL_PATH

app = FastAPI(title="Socrates Policy API")
action_model = None
mentor_state_model = None

MENTOR_STATE_TO_ACTION = {
    "accepted": "NO_FEEDBACK",
    "semantic_debug": "GUIDING_QUESTION",
    "execution_safety": "CODE_HIGHLIGHT",
    "efficiency_review": "CONCEPTUAL_HINT",
    "syntax_repair": "CODE_HIGHLIGHT",
}


class PolicyRequest(BaseModel):
    error_type: str | None = None
    severity: str | None = None
    compile_success: bool
    tests_passed: int
    tests_failed: int
    same_error_count: int
    total_errors_seen: int
    attempt_no: int
    last_feedback_action: str | None = None
    last_feedback_success: bool | None = None
    has_suspicious_region: bool
    code_lines: int
    total_feedback_count_in_session: int


class MentorStateRequest(BaseModel):
    code: str | None = None
    code_model_text: str | None = None
    problem_rating: int | float | None = None
    tags_text: str | None = None
    language_variant: str | None = "Python 3"
    compile_success: bool | None = None
    tests_passed: int | float | None = None
    tests_failed: int | float | None = None
    passed_test_count: int | float | None = None
    time_consumed_ms: int | float | None = None
    memory_consumed_bytes: int | float | None = None


@app.on_event("startup")
def load_model() -> None:
    global action_model, mentor_state_model
    if not MODEL_PATH.exists():
        raise RuntimeError(f"Model file not found: {MODEL_PATH.resolve()}")
    action_model = joblib.load(MODEL_PATH)

    if not MENTOR_STATE_MODEL_PATH.exists():
        raise RuntimeError(f"Mentor-state model file not found: {MENTOR_STATE_MODEL_PATH.resolve()}")
    mentor_state_model = joblib.load(MENTOR_STATE_MODEL_PATH)


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {
        "status": "ok",
        "action_model_loaded": action_model is not None,
        "mentor_state_model_loaded": mentor_state_model is not None,
    }


@app.post("/predict")
def predict(request: PolicyRequest) -> dict[str, str]:
    if action_model is None:
        raise HTTPException(status_code=503, detail="Model is not loaded")

    frame = pd.DataFrame([request.model_dump()])
    prediction = action_model.predict(frame)[0]
    return {"action": str(prediction)}


@app.post("/predict-mentor-state")
def predict_mentor_state(request: MentorStateRequest) -> dict[str, Any]:
    if mentor_state_model is None:
        raise HTTPException(status_code=503, detail="Mentor-state model is not loaded")

    frame = pd.DataFrame([build_mentor_state_features(request)])
    mentor_state = str(mentor_state_model.predict(frame)[0])
    class_scores, confidence = predict_scores(mentor_state_model, frame, mentor_state)
    action = MENTOR_STATE_TO_ACTION.get(mentor_state, "CONCEPTUAL_HINT")

    return {
        "mentor_state": mentor_state,
        "confidence": confidence,
        "class_scores": class_scores,
        "action": action,
        "model_version": "codeforces-source-v1",
    }


def build_mentor_state_features(request: MentorStateRequest) -> dict[str, Any]:
    code = request.code_model_text if request.code_model_text is not None else request.code
    code = code or ""
    metrics = source_metrics(code)
    return {
        "code_model_text": code[:5000],
        "problem_rating": request.problem_rating,
        "tags_text": request.tags_text or "",
        "language_variant": request.language_variant or "Python 3",
        "passed_test_count": first_not_none(request.passed_test_count, request.tests_passed),
        "time_consumed_ms": request.time_consumed_ms,
        "memory_consumed_bytes": request.memory_consumed_bytes,
        **metrics,
    }


def first_not_none(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value
    return None


def source_metrics(code: str) -> dict[str, int | float]:
    lines = code.splitlines() or [""]
    code_lines = len(lines)
    code_chars = len(code)
    return {
        "code_chars": code_chars,
        "code_lines": code_lines,
        "blank_line_count": sum(1 for line in lines if not line.strip()),
        "comment_line_count": sum(1 for line in lines if line.strip().startswith("#")),
        "avg_line_length": code_chars / max(code_lines, 1),
        "max_line_length": max((len(line) for line in lines), default=0),
        "branch_count": count_regex(code, r"\b(if|elif|else)\b"),
        "loop_count": count_regex(code, r"\b(for|while)\b"),
        "function_count": count_regex(code, r"\bdef\s+[A-Za-z_]\w*"),
        "class_count": count_regex(code, r"\bclass\s+[A-Za-z_]\w*"),
        "import_count": count_regex(code, r"\b(import|from)\b"),
        "input_count": count_regex(code, r"\binput\s*\("),
        "print_count": count_regex(code, r"\bprint\s*\("),
        "try_count": count_regex(code, r"\btry\s*:"),
        "except_count": count_regex(code, r"\bexcept\b"),
    }


def count_regex(text: str, pattern: str) -> int:
    return len(re.findall(pattern, text))


def predict_scores(model, frame: pd.DataFrame, mentor_state: str) -> tuple[dict[str, float], float | None]:
    if not hasattr(model, "predict_proba"):
        return {}, None

    probabilities = model.predict_proba(frame)[0]
    classes = getattr(model, "classes_", None)
    if classes is None and hasattr(model, "named_steps"):
        classes = getattr(model.named_steps.get("classifier"), "classes_", [])

    scores = {str(label): float(probability) for label, probability in zip(classes, probabilities)}
    confidence = scores.get(mentor_state)
    return scores, confidence
