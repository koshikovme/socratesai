from __future__ import annotations

import os
from pathlib import Path

import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = Path(os.getenv("POLICY_MODEL_PATH", str(BASE_DIR / "mentor_policy_model.joblib")))
if not MODEL_PATH.is_absolute():
    cwd_path = Path.cwd() / MODEL_PATH
    MODEL_PATH = cwd_path if cwd_path.exists() else BASE_DIR / MODEL_PATH

app = FastAPI(title="Socrates Policy API")
model = None


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


@app.on_event("startup")
def load_model() -> None:
    global model
    if not MODEL_PATH.exists():
        raise RuntimeError(f"Model file not found: {MODEL_PATH.resolve()}")
    model = joblib.load(MODEL_PATH)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/predict")
def predict(request: PolicyRequest) -> dict[str, str]:
    if model is None:
        raise HTTPException(status_code=503, detail="Model is not loaded")

    frame = pd.DataFrame([request.model_dump()])
    prediction = model.predict(frame)[0]
    return {"action": str(prediction)}
