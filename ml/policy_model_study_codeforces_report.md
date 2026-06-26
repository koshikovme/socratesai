# Policy Model Comparison and Ablation Study

- Dataset: `C:\Users\Koshikov\Desktop\socratesai\ml\codeforces_policy_dataset.csv`
- Rows: 60564
- Target column: `target_feedback_action`
- Split: `group_holdout`
- Group column: `problem_id`
- Train rows: 45231
- Test rows: 15333

## Target Distribution

| Label | Count |
|---|---:|
| `CODE_HIGHLIGHT` | 2111 |
| `CONCEPTUAL_HINT` | 15016 |
| `GUIDING_QUESTION` | 970 |
| `NO_FEEDBACK` | 42467 |

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
| `small_neural_classifier` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `small_neural_classifier` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `random_forest` | `without_analyzer_features` | ok | 0.9508 | 0.4782 |  |
| `small_neural_classifier` | `without_analyzer_features` | ok | 0.9508 | 0.4782 |  |
| `xgboost` | `without_analyzer_features` | ok | 0.9508 | 0.4782 |  |
| `lightgbm` | `without_analyzer_features` | ok | 0.7287 | 0.3013 |  |
| `logistic_regression` | `without_analyzer_features` | ok | 0.7078 | 0.2721 |  |

## Held-Out Groups

- Train groups: 1/C, 10/A, 10/C, 10/D, 10/E, 1000/A, 1000/C, 1000/D, 1000/E, 1000/G, 1003/B, 1003/C, 1003/E, 1004/A, 1004/B, 1004/C, 1004/E, 1004/F, 1005/A, 1005/B, ... (6887 total; 6867 more omitted)
- Test groups: 1/A, 10/B, 1000/B, 1000/F, 1003/A, 1003/D, 1003/F, 1004/D, 1005/E1, 1005/E2, 1006/A, 1006/E, 1007/A, 1007/D, 1009/D, 1009/E, 1009/G, 1010/A, 1010/D, 1011/A, ... (2296 total; 2276 more omitted)

## Interpretation Boundary

This study measures how well each model predicts the selected target action. With rubric labels it validates policy selection logic; with manually reviewed labels it becomes an expert-label benchmark. Learning gain still requires outcome labels and classroom/pilot data.
