# Mentor Policy Model Metrics

- Dataset: `ml\policy_dataset_expanded_full.csv`
- Dataset rows: 381003
- Train rows: 303515
- Test rows: 77488
- Target column: `feedback_action`
- Split strategy: `group_holdout`
- Group column: `problem_id`
- Accuracy: 1.0000
- Macro F1: 1.0000

## Target Distribution

| Label | Dataset | Train | Test |
|---|---:|---:|---:|
| `CODE_HIGHLIGHT` | 87914 | 70034 | 17880 |
| `CONCEPTUAL_HINT` | 117232 | 93399 | 23833 |
| `GUIDING_QUESTION` | 58625 | 46683 | 11942 |
| `NO_FEEDBACK` | 117232 | 93399 | 23833 |

## Per-Class Metrics

| Label | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 1.0000 | 1.0000 | 1.0000 | 17880 |
| `CONCEPTUAL_HINT` | 1.0000 | 1.0000 | 1.0000 | 23833 |
| `GUIDING_QUESTION` | 1.0000 | 1.0000 | 1.0000 | 11942 |
| `NO_FEEDBACK` | 1.0000 | 1.0000 | 1.0000 | 23833 |

## Confusion Matrix

Rows are true labels; columns are predicted labels.

| True \ Predicted | `CODE_HIGHLIGHT` | `CONCEPTUAL_HINT` | `GUIDING_QUESTION` | `NO_FEEDBACK` |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 17880 | 0 | 0 | 0 |
| `CONCEPTUAL_HINT` | 0 | 23833 | 0 | 0 |
| `GUIDING_QUESTION` | 0 | 0 | 11942 | 0 |
| `NO_FEEDBACK` | 0 | 0 | 0 | 23833 |

## Held-Out Groups

- Train groups: 0, 1, 10, 1000, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 101, 1011, 1012, 1013, 1014, 1015, 1016, 1017, ... (4000 total; 3980 more omitted)
- Test groups: 100, 1001, 1010, 1018, 1020, 1025, 1029, 1032, 1034, 1038, 1041, 1044, 1047, 1049, 1052, 1055, 1056, 1057, 106, 1073, ... (1000 total; 980 more omitted)

## Interpretation Boundary

These metrics evaluate action-label prediction for the policy selector. If the target is `feedback_action`, the model is imitating the current rule policy. If the target is `target_feedback_action`, the model is evaluated against reviewed or rubric labels. Neither setting by itself proves classroom learning gain.
