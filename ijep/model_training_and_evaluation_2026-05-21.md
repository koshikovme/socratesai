# Model Training And Evaluation Update - 2026-05-21

This note records the latest dataset expansion, ML training, model comparison, live Gemini evaluation, and methodological fixes for the IJEP paper.

## Executive Result

The project is ready for a solid system-evaluation article about a policy-guided programming mentor. It is not yet ready for a causal learning-gain claim because expert-reviewed classroom labels, inter-rater agreement, and student outcome labels are still missing.

The strongest valid runtime result is the fixed ML + Gemini benchmark:

| Metric | Value |
|---|---:|
| Events | 360 |
| Successful events | 360 |
| Runtime errors | 0 |
| Agreement with target action | 95.83% |
| Macro F1 | 95.80% |
| Mean latency | 1079.21 ms |
| P95 latency | 2072 ms |
| Gemini responses | 317 |
| Template fallbacks | 43 |

Results are stored in `experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml`.

## Dataset Expansion

I expanded the weak-label policy dataset from the local code corpus:

```powershell
python ml\build_synthetic_policy_dataset_from_code_corpus.py --input ml\train.csv --output ml\policy_dataset_expanded_full.csv --max-problems 5000 --solutions-per-problem 1000
```

The generated dataset uses all available unique solution sessions in `ml/train.csv`:

| Dataset | Rows | Coding sessions | Notes |
|---|---:|---:|---|
| `ml/policy_dataset_expanded_full.csv` | 381,003 | 117,232 | Full available corpus expansion |
| `ml/problem_suite_policy_dataset.csv` | 1,080 | 180 sessions | Rubric target-action benchmark |

The requested 10x expansion was not fully possible without duplicating records artificially. The full corpus expansion is about 8.9x larger than the earlier 42,861-row expanded dataset and is methodologically better because it avoids fake duplicated rows.

## ML Training

I trained a full weak-label policy model:

```powershell
python ml\train_policy_model.py --input ml\policy_dataset_expanded_full.csv --output-prefix ml\mentor_policy_model_expanded_full --target-column feedback_action --group-column problem_id
```

Result:

| Metric | Value |
|---|---:|
| Rows | 381,003 |
| Train rows | 303,515 |
| Test rows | 77,488 |
| Split | Group holdout by `problem_id` |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

Artifacts:

- `ml/mentor_policy_model_expanded_full.joblib`
- `ml/mentor_policy_model_expanded_full_metadata.json`
- `ml/mentor_policy_model_expanded_full_metrics.json`
- `ml/mentor_policy_model_expanded_full_report.md`
- `ml/mentor_policy_model_expanded_full_confusion_matrix.png`

I also retrained the rubric-target model:

```powershell
python ml\train_policy_model.py --input ml\problem_suite_policy_dataset.csv --output-prefix ml\problem_suite_policy_model --target-column target_feedback_action --group-column problem_slug
```

Result:

| Metric | Value |
|---|---:|
| Rows | 1,080 |
| Train rows | 810 |
| Test rows | 270 |
| Split | Group holdout by `problem_slug` |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

This result validates that the model can learn the current rubric action policy, but it must be framed as action-selection validity, not learning-gain evidence.

## Model Comparison And Ablation

I reran the model comparison study on both the rubric dataset and the full expanded weak-label dataset.

Rubric dataset command:

```powershell
python ml\run_policy_model_study.py --input ml\problem_suite_policy_dataset.csv --output-prefix ml\policy_model_study --target-column target_feedback_action --group-column problem_slug
```

Full expanded dataset command:

```powershell
python ml\run_policy_model_study.py --input ml\policy_dataset_expanded_full.csv --output-prefix ml\policy_model_study_expanded_full --target-column feedback_action --group-column problem_id
```

Compared models:

- Rule baseline
- Logistic Regression
- Random Forest
- XGBoost
- LightGBM
- Small neural classifier

Ablations:

- all features
- without history features
- without analyzer features
- without suspicious region
- without last feedback action

Important ablation result on the full expanded dataset: removing analyzer features drops models to about 0.9232 accuracy and 0.8813 macro F1. This is useful for the paper because it shows that analyzer-derived features are carrying meaningful signal.

Reports:

- `ml/policy_model_study_report.md`
- `ml/policy_model_study_results.json`
- `ml/policy_model_study_expanded_full_report.md`
- `ml/policy_model_study_expanded_full_results.json`

## Backend Fix For Valid ML Runtime Testing

I found a methodological bug in the earlier runtime ML benchmark: the Spring backend sent camelCase JSON to the FastAPI policy service, while the policy service expected snake_case fields. That caused HTTP 422 errors and fallback to the rule policy.

I fixed this by changing the backend ML payload mapping to snake_case and adding a regression test.

Changed files:

- `src/main/java/com/masters/socratesai/mentor/policy/MlPolicySelector.java`
- `src/test/java/com/masters/socratesai/mentor/policy/MentorPolicyServiceTest.java`

I also exposed feedback source and captured generated feedback text in benchmark outputs:

- `src/main/java/com/masters/socratesai/mentor/dto/MentorResponse.java`
- `src/main/java/com/masters/socratesai/mentor/service/MentorService.java`
- `experiments/problem_suite_http_benchmark.py`

After the fix, the Java test suite passed:

| Test suite | Result |
|---|---:|
| Maven tests | 74 tests, 0 failures, 0 errors, 1 skipped |

## Live Gemini Benchmark

I rebuilt and ran the backend with Docker Compose, PostgreSQL, the policy API, fixed ML payloads, and Gemini 2.5 Flash-Lite feedback generation.

Valid benchmark command:

```powershell
python experiments\problem_suite_http_benchmark.py --base-url http://localhost:18080 --mode ML --output-dir experiments\results\problem-suite-gemini-quality-fixed-fresh-5c\ml --cohorts 5 --student-offset 910000 --environment "Docker Compose backend on 18080, PostgreSQL, fixed ML policy API contract, fresh student ids, Gemini 2.5 Flash-Lite, feedback text captured"
```

Valid benchmark result:

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.24% | 100.00% | 97.56% | 100 |
| `CONCEPTUAL_HINT` | 93.10% | 96.43% | 94.74% | 140 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 60 |
| `NO_FEEDBACK` | 100.00% | 83.33% | 90.91% | 60 |

The remaining 15 disagreements are concentrated in:

| Case | Count |
|---|---:|
| `lru-cache`, first conceptual error | 5 |
| `lru-cache`, local completion | 5 |
| `valid-parentheses`, local completion | 5 |

This confirms that the remaining weak spot is the `NO_FEEDBACK` or silence decision for locally complete code states and some unknown-logic states.

## Results Not To Use As Primary Evidence

Do not use the old 720-event ML result as the primary ML result. It was collected before the backend ML JSON contract fix and should be treated as a pre-fix fallback run.

Also do not use the 72-event fixed run with reused student ids as the main result. It produced 51.39% agreement because previous database history contaminated the learner-state features. The fresh-student benchmark is the valid one.

## Paper-Ready Claim

Safe claim:

> The system selects pedagogical feedback actions with high agreement against rubric action labels in a controlled programming problem-suite benchmark, while generating concise LLM-based feedback text and preserving a deterministic template fallback.

Unsafe claim:

> The system improves student learning outcomes.

That stronger claim needs a real pilot with pre-test, mentor practice, post-test, helpfulness survey, and outcome labels.

## Next Work For A Stronger Article

1. Manually review 200-500 generated feedback events.
2. Have two raters label 50-100 overlapping events and report Cohen's kappa.
3. Add outcome labels: helpfulness, fixed-after-feedback, time-to-fix, repeated same error.
4. Improve `NO_FEEDBACK` behavior so local-completion cases can remain silent.
5. Log Gemini fallback reason explicitly so the paper can report provider failure, timeout, safety block, or empty response rates separately.
