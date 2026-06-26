# Policy Model Comparison and Ablation Study

- Dataset: `ml\problem_suite_policy_dataset.csv`
- Rows: 1080
- Target column: `target_feedback_action`
- Split: `group_holdout`
- Group column: `problem_slug`
- Train rows: 810
- Test rows: 270

## Target Distribution

| Label | Count |
|---|---:|
| `CODE_HIGHLIGHT` | 300 |
| `CONCEPTUAL_HINT` | 420 |
| `GUIDING_QUESTION` | 180 |
| `NO_FEEDBACK` | 180 |

## Model Results

| Model | Feature Set | Status | Accuracy | Macro F1 | Notes |
|---|---|---|---:|---:|---|
| `lightgbm` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_analyzer_features` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `logistic_regression` | `without_analyzer_features` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_analyzer_features` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_analyzer_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_analyzer_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_history_features` | ok | 0.9963 | 0.9971 |  |
| `small_neural_classifier` | `all_features` | ok | 0.9963 | 0.9971 |  |
| `logistic_regression` | `without_history_features` | ok | 0.9926 | 0.9943 |  |
| `small_neural_classifier` | `without_history_features` | ok | 0.9926 | 0.9943 |  |
| `small_neural_classifier` | `without_last_feedback_action` | ok | 0.9926 | 0.9943 |  |
| `logistic_regression` | `without_suspicious_region` | ok | 0.9852 | 0.9885 |  |
| `logistic_regression` | `all_features` | ok | 0.9519 | 0.9617 |  |
| `logistic_regression` | `without_last_feedback_action` | ok | 0.9444 | 0.9556 |  |
| `small_neural_classifier` | `without_suspicious_region` | ok | 0.9444 | 0.9556 |  |
| `rule_baseline` | `all_features` | ok | 0.9444 | 0.9333 |  |

## Held-Out Groups

- Train groups: climbing-stairs, longest-substring, lru-cache, matrix-diagonal-sum, merge-sorted-array, palindrome, reverse-linked-list, roman-to-integer, valid-parentheses
- Test groups: binary-search, trapping-rain-water, two-sum

## Interpretation Boundary

This study measures how well each model predicts the selected target action. With rubric labels it validates policy selection logic; with manually reviewed labels it becomes an expert-label benchmark. Learning gain still requires outcome labels and classroom/pilot data.
