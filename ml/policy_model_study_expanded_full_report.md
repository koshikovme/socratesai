# Policy Model Comparison and Ablation Study

- Dataset: `ml\policy_dataset_expanded_full.csv`
- Rows: 381003
- Target column: `feedback_action`
- Split: `group_holdout`
- Group column: `problem_id`
- Train rows: 279520
- Test rows: 101483

## Target Distribution

| Label | Count |
|---|---:|
| `CODE_HIGHLIGHT` | 87914 |
| `CONCEPTUAL_HINT` | 117232 |
| `GUIDING_QUESTION` | 58625 |
| `NO_FEEDBACK` | 117232 |

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
| `small_neural_classifier` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `all_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_history_features` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_last_feedback_action` | ok | 1.0000 | 1.0000 |  |
| `xgboost` | `without_suspicious_region` | ok | 1.0000 | 1.0000 |  |
| `lightgbm` | `without_analyzer_features` | ok | 0.9232 | 0.8813 |  |
| `logistic_regression` | `without_analyzer_features` | ok | 0.9232 | 0.8813 |  |
| `random_forest` | `without_analyzer_features` | ok | 0.9232 | 0.8813 |  |
| `small_neural_classifier` | `without_analyzer_features` | ok | 0.9232 | 0.8813 |  |
| `xgboost` | `without_analyzer_features` | ok | 0.9232 | 0.8813 |  |

## Held-Out Groups

- Train groups: 0, 1, 10, 1000, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 101, 1011, 1012, 1013, 1014, 1015, 1016, 1017, ... (3750 total; 3730 more omitted)
- Test groups: 100, 1001, 1010, 1018, 1020, 1024, 1025, 1027, 1029, 1032, 1034, 1038, 1041, 1042, 1044, 1047, 1049, 1052, 1055, 1056, ... (1250 total; 1230 more omitted)

## Interpretation Boundary

This study measures how well each model predicts the selected target action. With rubric labels it validates policy selection logic; with manually reviewed labels it becomes an expert-label benchmark. Learning gain still requires outcome labels and classroom/pilot data.
