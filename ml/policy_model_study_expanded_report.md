# Policy Model Comparison and Ablation Study

- Dataset: `ml\policy_dataset_expanded.csv`
- Rows: 42861
- Target column: `feedback_action`
- Split: `group_holdout`
- Group column: `problem_id`
- Train rows: 32166
- Test rows: 10695

## Target Distribution

| Label | Count |
|---|---:|
| `CODE_HIGHLIGHT` | 9890 |
| `CONCEPTUAL_HINT` | 13188 |
| `GUIDING_QUESTION` | 6595 |
| `NO_FEEDBACK` | 13188 |

## Model Results

| Model | Feature Set | Status | Accuracy | Macro F1 | Notes |
|---|---|---|---:|---:|---|
| `lightgbm` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `logistic_regression` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `logistic_regression` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `logistic_regression` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `logistic_regression` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `rule_baseline` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_suspicious_region` | ok | 0.9999 | 0.9999 |  |
| `lightgbm` | `without_analyzer_features` | ok | 0.9239 | 0.8823 |  |
| `logistic_regression` | `without_analyzer_features` | ok | 0.9239 | 0.8823 |  |
| `random_forest` | `without_analyzer_features` | ok | 0.9239 | 0.8823 |  |
| `small_neural_classifier` | `without_analyzer_features` | ok | 0.9239 | 0.8823 |  |
| `xgboost` | `without_analyzer_features` | ok | 0.9239 | 0.8823 |  |

## Held-Out Groups

- Train groups: 1, 10, 1000, 1002, 1003, 1004, 1006, 1007, 1008, 1009, 101, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1019, 1020, ... (2250 total; 2230 more omitted)
- Test groups: 0, 100, 1001, 1005, 1010, 1018, 102, 1023, 1025, 1027, 1029, 1034, 1036, 104, 1041, 1044, 1047, 1055, 1057, 1064, ... (750 total; 730 more omitted)

## Interpretation Boundary

This study measures how well each model predicts the selected target action. With rubric labels it validates policy selection logic; with manually reviewed labels it becomes an expert-label benchmark. Learning gain still requires outcome labels and classroom/pilot data.
