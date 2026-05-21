# Policy ML

Runtime features come from Spring `PolicyFeatures` and are exported from `interaction_logs`.

## Train

1. Export a CSV from Postgres using `export_policy_dataset.sql`.
2. Put the file near `train_policy_model.py` as `policy_dataset.csv`.
3. Install dependencies:

```bash
pip install -r requirements.txt
```

4. Train:

```bash
python train_policy_model.py
```

This creates:
- `mentor_policy_model.joblib`
- `mentor_policy_model_metadata.json`
- `mentor_policy_model_metrics.json`
- `mentor_policy_model_report.md`
- `mentor_policy_model_confusion_matrix.png`

You can also train from a specific dataset and keep the output separate:

```bash
python train_policy_model.py --input problem_suite_policy_dataset.csv --output-prefix problem_suite_policy_model --target-column target_feedback_action --group-column problem_slug
```

## Run predictor

```bash
uvicorn policy_api:app --host 0.0.0.0 --port 8001
```

Spring is configured to call `http://localhost:8001/predict`.

To run the predictor with a non-default model:

```bash
POLICY_MODEL_PATH=problem_suite_policy_model.joblib uvicorn policy_api:app --host 0.0.0.0 --port 8001
```

On PowerShell:

```powershell
$env:POLICY_MODEL_PATH = ".\problem_suite_policy_model.joblib"
uvicorn policy_api:app --host 0.0.0.0 --port 8001
```

## Problem-suite logs -> supervised policy dataset

The controlled problem-suite benchmark writes real HTTP mentor events to:

```text
experiments/results/problem-suite-1080/rule/events.csv
```

Generate the 1,080-event log with 15 cohorts:

```bash
python ../experiments/problem_suite_http_benchmark.py --base-url http://localhost:18080 --mode RULE --cohorts 15 --student-offset 500000 --output-dir ../experiments/results/problem-suite-1080/rule
```

Build a supervised policy CSV from that event log:

```bash
python build_problem_suite_policy_dataset.py --events ../experiments/results/problem-suite-1080/rule/events.csv --output problem_suite_policy_dataset.csv
```

Then train with problem-level holdout:

```bash
python train_policy_model.py --input problem_suite_policy_dataset.csv --output-prefix problem_suite_policy_model --target-column target_feedback_action --group-column problem_slug
```

Run the model comparison and ablation study:

```bash
python run_policy_model_study.py --input problem_suite_policy_dataset.csv --output-prefix policy_model_study --target-column target_feedback_action --group-column problem_slug
```

Current generated artifacts:
- `problem_suite_policy_dataset.csv` - 1,080 rows from 12 programming problems
- `problem_suite_policy_model.joblib`
- `problem_suite_policy_model_metrics.json`
- `problem_suite_policy_model_report.md`
- `problem_suite_policy_model_confusion_matrix.png`
- `policy_model_study_results.json`
- `policy_model_study_report.md`

For a supervisor demo, see `ML_RESULTS.md`.

## Public code corpus -> synthetic policy dataset

If you have a public problem-and-solution corpus such as `train.csv` with columns like:
- `problem_id`
- `solutions`
- `difficulty`
- `starter_code`

you can build a synthetic weak-label dataset for the policy model:

```bash
python build_synthetic_policy_dataset_from_code_corpus.py --input train.csv --output policy_dataset.csv --max-problems 1000 --solutions-per-problem 3
Copy-Item policy_dataset.csv policy_dataset.csv
python train_policy_model.py
```

What this script does:
- takes accepted solutions from a public code corpus
- synthesizes novice-like error states and short attempt chains
- derives proxy policy labels such as `CODE_HIGHLIGHT`, `CONCEPTUAL_HINT`, `GUIDING_QUESTION`, `NO_FEEDBACK`
- exports a CSV compatible with `policy_api.py` and `train_policy_model.py`

Use honest wording in the paper:
- `synthetic weakly supervised policy dataset`
- `derived from a public programming-problem corpus`
- `accepted solutions were used to synthesize novice-like error states`
- `labels are proxy pedagogical actions, not authentic tutor annotations`

Do not claim:
- real student interaction logs
- authentic pedagogical labels
- classroom-validated tutoring effectiveness from this dataset alone

## Export without psql

If you want to export directly from Spring Boot, use:

```bash
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/interactions/policy-dataset" -o policy_dataset.csv
```

For a cleaner successful-only weak-label dataset:

```bash
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/interactions/policy-dataset?resolvedOnly=true" -o policy_dataset_successful.csv
```

## Public-dataset fallback: Project CodeNet

If you urgently need a public dataset for a paper-quality feasibility experiment, use Project CodeNet metadata and derive weak pedagogical labels.

Recommended source:
- Project CodeNet: https://github.com/IBM/Project_CodeNet

### What this gives you

This is not a true pedagogical ground-truth dataset.
It is a weakly supervised proxy dataset for action selection.
Use honest wording in the paper such as:
- `weakly supervised policy dataset derived from Project CodeNet metadata`
- `preliminary ML policy classifier`
- `proxy pedagogical labels`

### Weak-label mapping used by the script

`build_codenet_policy_dataset.py` maps submission outcomes to proxy pedagogical actions:
- `Compile Error` -> `CODE_HIGHLIGHT`
- first `Wrong Answer` -> `CONCEPTUAL_HINT`
- repeated `Wrong Answer` -> `GUIDING_QUESTION`
- `Runtime Error` / `TLE` / `MLE` -> `CONCEPTUAL_HINT` or `GUIDING_QUESTION`
- `Accepted` -> `NO_FEEDBACK`

It also maps outcome categories to proxy analyzer features such as:
- `SYNTAX_ERROR`
- `WRONG_CONDITION`
- `STUCK_NO_PROGRESS`
- `SUCCESS`

### Build a training CSV from Project CodeNet metadata

If you already have a concatenated metadata CSV with columns such as `user_id`, `problem_id`, `language`, `status`, `date`, `accuracy`, `code_size`, run:

```bash
python build_codenet_policy_dataset.py --input codenet_metadata.csv --output policy_dataset_codenet.csv --language Java
```

Then train on it:

```bash
python train_policy_model.py
```

If the input file contains metadata for only one problem and has no `problem_id` column:

```bash
python build_codenet_policy_dataset.py --input p00001.csv --problem-id p00001 --output policy_dataset_codenet.csv --language Java
```

After generating `policy_dataset_codenet.csv`, rename or copy it to `policy_dataset.csv` before training.

## What to feed the model

One interaction log row is one training example.

Use these columns as `X` features:
- `error_type`
- `severity`
- `compile_success`
- `tests_passed`
- `tests_failed`
- `same_error_count`
- `total_errors_seen`
- `attempt_no`
- `last_feedback_action`
- `last_feedback_success`
- `has_suspicious_region`
- `code_lines`
- `total_feedback_count_in_session`

Do not use as `X` in v1:
- `feedback_text`
- `suspicious_region`
- `interaction_id`
- `created_at`
- `policy_version`

`feedback_text` is realization text, not policy state.
`interaction_id` and timestamps are mostly identifiers unless you engineer extra temporal features on purpose.

## Label strategy

### V1: weak labels

Use:
- `y = feedback_action`

This trains an imitation model of the current rule-based selector.
It is useful for:
- validating the ML pipeline
- comparing rule vs learned selector latency and consistency
- getting a first baseline model without manual annotation

It is not yet a better-than-rules policy by itself.

### V2: filtered weak labels

Use the same `y = feedback_action`, but prefer rows where:
- `resolved_after_feedback = true`

Keep for evaluation or later weighting:
- `fixed_after_ms`

This gives cleaner training data because the chosen action at least correlates with a successful outcome.

### V3: proper supervised labels

Add a reviewed column:
- `target_feedback_action`

Then train with:
- `y = target_feedback_action`

`train_policy_model.py` already prefers `target_feedback_action` over `feedback_action` if the column exists.

## Practical recommendation

Start with three datasets:

1. `all_interactions.csv`
Contains all rows with `feedback_action`. Use this for imitation baseline.

2. `successful_interactions.csv`
Contains rows with `resolved_after_feedback = true`. Use this for a cleaner exploratory model.

3. `reviewed_interactions.csv`
Same export, but with manually added `target_feedback_action`. Use this for your paper-quality supervised model.

## What `interaction_logs` is good for

Current `interaction_logs` is good enough for:
- weak-supervision policy learning
- offline comparison of `rule` vs `ml`
- feature engineering experiments

Current `interaction_logs` is still weak for:
- proving pedagogical optimality
- claiming ground-truth labels without human review
- learning nuanced tutoring strategy from outcome alone
