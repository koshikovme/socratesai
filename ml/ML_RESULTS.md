# SocratesAI ML Policy Training Results

This note is the practical demo path for showing how the ML policy data is obtained, how the model is trained, and what metrics are produced.

## Data Sources

1. `ml/policy_dataset.csv`
   - Rows: 9,130
   - Label: `feedback_action`
   - Purpose: original weak-supervision / rule-policy imitation baseline.
   - Meaning: the model learns to reproduce the current policy behavior from exported interaction-style features.

2. `ml/policy_dataset_expanded.csv`
   - Rows: 42,861
   - Label: `feedback_action`
   - Purpose: expanded weak-supervision / rule-policy imitation corpus derived from the public programming-problem corpus.
   - Meaning: useful for ML pipeline stability and ablation checks, but still proxy labels rather than expert labels.

3. `experiments/results/problem-suite-1080/rule/events.csv`
   - Rows: 1,080 successful real-HTTP mentor events.
   - Source: the running Spring Boot application, authenticated mentor endpoint, PostgreSQL-backed evaluation run.
   - Coverage: 12 programming problems, 15 cohorts, 6 attempt states per problem.
   - Problems: palindrome, two-sum, valid parentheses, binary search, merge sorted array, trapping rain water, LRU cache, longest substring, reverse linked list, matrix diagonal sum, roman-to-integer, climbing stairs.

4. `ml/problem_suite_policy_dataset.csv`
   - Rows: 1,080
   - Built from: `experiments/results/problem-suite-1080/rule/events.csv`
   - Label: `target_feedback_action`
   - Purpose: supervised policy check against explicit review-rubric action labels.

## Reproduce The Dataset

First generate the 1,080 real-HTTP events. In one PowerShell terminal:

```powershell
docker compose up -d pg_db

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SERVER_PORT = "18080"
$env:APP_POLICY_MODE = "rule"
$env:APP_GEMINI_ENABLED = "false"
$env:APP_OPENAI_ENABLED = "false"
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
python .\experiments\problem_suite_http_benchmark.py `
  --base-url http://localhost:18080 `
  --mode RULE `
  --cohorts 15 `
  --student-offset 500000 `
  --output-dir .\experiments\results\problem-suite-1080\rule `
  --environment "Real HTTP Spring Boot process, PostgreSQL 16.11 Docker Compose database, 12-problem programming suite, 15 cohorts"
```

Then build the ML-ready CSV:

```powershell
python .\ml\build_problem_suite_policy_dataset.py `
  --events .\experiments\results\problem-suite-1080\rule\events.csv `
  --output .\ml\problem_suite_policy_dataset.csv
```

Outputs:
- `ml/problem_suite_policy_dataset.csv`
- `ml/problem_suite_policy_dataset_summary.json`
- `ml/problem_suite_policy_dataset_report.md`

## Run Model Comparison And Ablation Study

```powershell
python .\ml\run_policy_model_study.py `
  --input .\ml\problem_suite_policy_dataset.csv `
  --output-prefix .\ml\policy_model_study `
  --target-column target_feedback_action `
  --group-column problem_slug
```

Artifacts:
- `ml/policy_model_study_results.json`
- `ml/policy_model_study_report.md`

Current model comparison on the 1,080-row problem-suite dataset:

| Model | Best feature set | Accuracy | Macro F1 |
|---|---|---:|---:|
| `rule_baseline` | `all_features` | 0.9444 | 0.9333 |
| `logistic_regression` | `without_analyzer_features` | 1.0000 | 1.0000 |
| `random_forest` | `all_features` | 1.0000 | 1.0000 |
| `xgboost` | `all_features` | 1.0000 | 1.0000 |
| `lightgbm` | `all_features` | 1.0000 | 1.0000 |
| `small_neural_classifier` | `without_analyzer_features` | 1.0000 | 1.0000 |

The study also runs ablations without history features, analyzer features, suspicious-region features, and last-feedback-action features.

## Train The Supervised Problem-Suite Model

```powershell
python .\ml\train_policy_model.py `
  --input .\ml\problem_suite_policy_dataset.csv `
  --output-prefix .\ml\problem_suite_policy_model `
  --target-column target_feedback_action `
  --group-column problem_slug
```

The `problem_slug` group holdout means entire programming problems are held out for testing instead of randomly mixing attempts from every problem into train and test.

## Supervised Problem-Suite Result

| Metric | Value |
|---|---:|
| Dataset rows | 1,080 |
| Train rows | 810 |
| Test rows | 270 |
| Split | problem-level group holdout |
| Held-out problems | binary-search, trapping-rain-water, two-sum |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

Per-class F1:

| Action | F1 | Test support |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 1.0000 | 75 |
| `CONCEPTUAL_HINT` | 1.0000 | 105 |
| `GUIDING_QUESTION` | 1.0000 | 45 |
| `NO_FEEDBACK` | 1.0000 | 45 |

Artifacts:
- `ml/problem_suite_policy_model.joblib`
- `ml/problem_suite_policy_model_metrics.json`
- `ml/problem_suite_policy_model_report.md`
- `ml/problem_suite_policy_model_confusion_matrix.png`

## Train The Larger Imitation Model

```powershell
python .\ml\train_policy_model.py `
  --input .\ml\policy_dataset_expanded.csv `
  --output-prefix .\ml\mentor_policy_model_expanded `
  --target-column feedback_action `
  --group-column problem_id
```

Result:

| Metric | Value |
|---|---:|
| Dataset rows | 42,861 |
| Train rows | 34,325 |
| Test rows | 8,536 |
| Split | problem-level group holdout |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

This is expected because the labels are weak labels derived from the current policy logic. It validates that the ML inference service can reproduce the rule policy before deployment.

Expanded artifacts:
- `ml/policy_dataset_expanded.csv`
- `ml/mentor_policy_model_expanded.joblib`
- `ml/mentor_policy_model_expanded_metrics.json`
- `ml/mentor_policy_model_expanded_report.md`
- `ml/mentor_policy_model_expanded_confusion_matrix.png`
- `ml/policy_model_study_expanded_results.json`
- `ml/policy_model_study_expanded_report.md`

## Run FastAPI With A Specific Model

Default model:

```powershell
uvicorn ml.policy_api:app --host 0.0.0.0 --port 8001
```

Problem-suite trained model:

```powershell
$env:POLICY_MODEL_PATH = ".\ml\problem_suite_policy_model.joblib"
uvicorn ml.policy_api:app --host 0.0.0.0 --port 8001
```

Quick smoke check:

```powershell
python .\ml\test_policy_model.py

$env:POLICY_MODEL_PATH = ".\ml\problem_suite_policy_model.joblib"
python .\ml\test_policy_model.py
```

## What To Say In The Paper Or Demo

Use:
- `real-HTTP controlled problem-suite evaluation`
- `policy classifier trained on exported interaction features`
- `target_feedback_action labels from an explicit review rubric`
- `weak-supervision imitation model for deployment safety`
- `problem-level holdout evaluation`

Avoid claiming:
- semester-long classroom deployment
- independent instructor ground truth
- learning gain or retention improvement
- real student outcome labels, unless those labels are collected later

The strongest current ML claim is:

> The learned policy can reproduce the intended action-selection behavior from runtime features, and the training pipeline is reproducible from exported application events. The result is policy-quality evidence, not yet learning-outcome evidence.
