# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM python:3.12-slim-bookworm@sha256:e697c81897302a4c4333bade000d98038fbb21eb064f8ee1fd42d02ebcd9d337 AS build
WORKDIR /build
COPY requirements-serving.lock ./
RUN --mount=type=cache,target=/root/.cache/pip python -m pip wheel --require-hashes --only-binary=:all: --wheel-dir=/wheels -r requirements-serving.lock

FROM python:3.12-slim-bookworm@sha256:e697c81897302a4c4333bade000d98038fbb21eb064f8ee1fd42d02ebcd9d337 AS runtime
# CVE-2026-109518: use authenticated transport to install the Debian security fix.
RUN sed -i 's|http://deb.debian.org|https://deb.debian.org|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update && apt-get install -y --no-install-recommends apt=2.6.2 libapt-pkg6.0=2.6.2 \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /wheels /wheels
COPY requirements-serving.lock ./
RUN python -m pip install --no-cache-dir --no-index --find-links=/wheels --require-hashes -r requirements-serving.lock && rm -rf /wheels
COPY policy_api.py ./
COPY mentor_policy_model.joblib models/mentor_policy_model.joblib
COPY codeforces_feedback_need/source_code_model.joblib models/source_code_model.joblib
COPY models.manifest.json models/manifest.json
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 \
    POLICY_MODEL_PATH=/app/models/mentor_policy_model.joblib MENTOR_STATE_MODEL_PATH=/app/models/source_code_model.joblib \
    MODEL_CHECKSUM_MANIFEST=/app/models/manifest.json
USER 10001:10001
EXPOSE 8001
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/ready', timeout=3)"]
CMD ["uvicorn", "policy_api:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1", "--no-access-log"]
