# Mentor Policy Model Metrics

- Dataset: `ml\policy_dataset_expanded.csv`
- Dataset rows: 42861
- Train rows: 34325
- Test rows: 8536
- Target column: `feedback_action`
- Split strategy: `group_holdout`
- Group column: `problem_id`
- Accuracy: 1.0000
- Macro F1: 1.0000

## Target Distribution

| Label | Dataset | Train | Test |
|---|---:|---:|---:|
| `CODE_HIGHLIGHT` | 9890 | 7921 | 1969 |
| `CONCEPTUAL_HINT` | 13188 | 10562 | 2626 |
| `GUIDING_QUESTION` | 6595 | 5280 | 1315 |
| `NO_FEEDBACK` | 13188 | 10562 | 2626 |

## Per-Class Metrics

| Label | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 1.0000 | 1.0000 | 1.0000 | 1969 |
| `CONCEPTUAL_HINT` | 1.0000 | 1.0000 | 1.0000 | 2626 |
| `GUIDING_QUESTION` | 1.0000 | 1.0000 | 1.0000 | 1315 |
| `NO_FEEDBACK` | 1.0000 | 1.0000 | 1.0000 | 2626 |

## Confusion Matrix

Rows are true labels; columns are predicted labels.

| True \ Predicted | `CODE_HIGHLIGHT` | `CONCEPTUAL_HINT` | `GUIDING_QUESTION` | `NO_FEEDBACK` |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 1969 | 0 | 0 | 0 |
| `CONCEPTUAL_HINT` | 0 | 2626 | 0 | 0 |
| `GUIDING_QUESTION` | 0 | 0 | 1315 | 0 |
| `NO_FEEDBACK` | 0 | 0 | 0 | 2626 |

## Held-Out Groups

- Train groups: 1, 10, 100, 1000, 1002, 1003, 1004, 1006, 1007, 1008, 1009, 101, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, ... (2400 total; 2380 more omitted)
- Test groups: 0, 1001, 1005, 102, 1025, 1027, 1034, 1036, 104, 1041, 1044, 1047, 1055, 1057, 1064, 1073, 1078, 108, 1080, 1084, ... (600 total; 580 more omitted)

## Interpretation Boundary

These metrics evaluate action-label prediction for the policy selector. If the target is `feedback_action`, the model is imitating the current rule policy. If the target is `target_feedback_action`, the model is evaluated against reviewed or rubric labels. Neither setting by itself proves classroom learning gain.
