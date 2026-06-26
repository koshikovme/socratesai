# Mentor Policy Model Metrics

- Dataset: `C:\Users\Koshikov\Desktop\socratesai\ml\codeforces_policy_dataset.csv`
- Dataset rows: 60564
- Train rows: 48314
- Test rows: 12250
- Target column: `target_feedback_action`
- Split strategy: `group_holdout`
- Group column: `problem_id`
- Accuracy: 1.0000
- Macro F1: 1.0000

## Target Distribution

| Label | Dataset | Train | Test |
|---|---:|---:|---:|
| `CODE_HIGHLIGHT` | 2111 | 1697 | 414 |
| `CONCEPTUAL_HINT` | 15016 | 11857 | 3159 |
| `GUIDING_QUESTION` | 970 | 789 | 181 |
| `NO_FEEDBACK` | 42467 | 33971 | 8496 |

## Per-Class Metrics

| Label | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 1.0000 | 1.0000 | 1.0000 | 414 |
| `CONCEPTUAL_HINT` | 1.0000 | 1.0000 | 1.0000 | 3159 |
| `GUIDING_QUESTION` | 1.0000 | 1.0000 | 1.0000 | 181 |
| `NO_FEEDBACK` | 1.0000 | 1.0000 | 1.0000 | 8496 |

## Confusion Matrix

Rows are true labels; columns are predicted labels.

| True \ Predicted | `CODE_HIGHLIGHT` | `CONCEPTUAL_HINT` | `GUIDING_QUESTION` | `NO_FEEDBACK` |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 414 | 0 | 0 | 0 |
| `CONCEPTUAL_HINT` | 0 | 3159 | 0 | 0 |
| `GUIDING_QUESTION` | 0 | 0 | 181 | 0 |
| `NO_FEEDBACK` | 0 | 0 | 0 | 8496 |

## Held-Out Groups

- Train groups: 1/C, 10/A, 10/B, 10/C, 10/D, 10/E, 1000/A, 1000/C, 1000/D, 1000/E, 1000/G, 1003/B, 1003/C, 1003/E, 1004/A, 1004/B, 1004/C, 1004/E, 1004/F, 1005/A, ... (7346 total; 7326 more omitted)
- Test groups: 1/A, 1000/B, 1000/F, 1003/A, 1003/D, 1003/F, 1004/D, 1005/E1, 1005/E2, 1006/A, 1006/E, 1007/A, 1007/D, 1009/D, 1009/E, 1009/G, 1011/A, 1012/C, 1013/A, 1015/D, ... (1837 total; 1817 more omitted)

## Interpretation Boundary

These metrics evaluate action-label prediction for the policy selector. If the target is `feedback_action`, the model is imitating the current rule policy. If the target is `target_feedback_action`, the model is evaluated against reviewed or rubric labels. Neither setting by itself proves classroom learning gain.
