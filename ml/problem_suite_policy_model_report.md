# Mentor Policy Model Metrics

- Dataset: `ml\problem_suite_policy_dataset.csv`
- Dataset rows: 1080
- Train rows: 810
- Test rows: 270
- Target column: `target_feedback_action`
- Split strategy: `group_holdout`
- Group column: `problem_slug`
- Accuracy: 1.0000
- Macro F1: 1.0000

## Target Distribution

| Label | Dataset | Train | Test |
|---|---:|---:|---:|
| `CODE_HIGHLIGHT` | 300 | 225 | 75 |
| `CONCEPTUAL_HINT` | 420 | 315 | 105 |
| `GUIDING_QUESTION` | 180 | 135 | 45 |
| `NO_FEEDBACK` | 180 | 135 | 45 |

## Per-Class Metrics

| Label | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 1.0000 | 1.0000 | 1.0000 | 75 |
| `CONCEPTUAL_HINT` | 1.0000 | 1.0000 | 1.0000 | 105 |
| `GUIDING_QUESTION` | 1.0000 | 1.0000 | 1.0000 | 45 |
| `NO_FEEDBACK` | 1.0000 | 1.0000 | 1.0000 | 45 |

## Confusion Matrix

Rows are true labels; columns are predicted labels.

| True \ Predicted | `CODE_HIGHLIGHT` | `CONCEPTUAL_HINT` | `GUIDING_QUESTION` | `NO_FEEDBACK` |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 75 | 0 | 0 | 0 |
| `CONCEPTUAL_HINT` | 0 | 105 | 0 | 0 |
| `GUIDING_QUESTION` | 0 | 0 | 45 | 0 |
| `NO_FEEDBACK` | 0 | 0 | 0 | 45 |

## Held-Out Groups

- Train groups: climbing-stairs, longest-substring, lru-cache, matrix-diagonal-sum, merge-sorted-array, palindrome, reverse-linked-list, roman-to-integer, valid-parentheses
- Test groups: binary-search, trapping-rain-water, two-sum

## Interpretation Boundary

These metrics evaluate action-label prediction for the policy selector. If the target is `feedback_action`, the model is imitating the current rule policy. If the target is `target_feedback_action`, the model is evaluated against reviewed or rubric labels. Neither setting by itself proves classroom learning gain.
