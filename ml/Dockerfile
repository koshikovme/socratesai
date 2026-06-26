FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV POLICY_MODEL_PATH=/app/problem_suite_policy_model.joblib
ENV MENTOR_STATE_MODEL_PATH=/app/codeforces_feedback_need/source_code_model.joblib

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY policy_api.py ./
COPY mentor_policy_model.joblib ./
COPY problem_suite_policy_model.joblib ./
RUN mkdir -p codeforces_feedback_need
COPY codeforces_feedback_need/*.joblib ./codeforces_feedback_need/

EXPOSE 8001

CMD ["uvicorn", "policy_api:app", "--host", "0.0.0.0", "--port", "8001"]
