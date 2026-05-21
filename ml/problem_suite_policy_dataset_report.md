# Problem-Suite Policy Dataset

- Generated at: 2026-05-19T11:42:26.807504+00:00
- Rows: 1080
- Sessions: 180
- Programming problems: 12
- Source files: experiments\results\problem-suite-1080\rule\events.csv
- App action vs rubric agreement: 87.50%

## Target Label Distribution

| Action | Rows |
|---|---:|
| `CODE_HIGHLIGHT` | 300 |
| `CONCEPTUAL_HINT` | 420 |
| `GUIDING_QUESTION` | 180 |
| `NO_FEEDBACK` | 180 |

## Actual App Action Distribution

| Action | Rows |
|---|---:|
| `CODE_HIGHLIGHT` | 300 |
| `CONCEPTUAL_HINT` | 435 |
| `GUIDING_QUESTION` | 180 |
| `NO_FEEDBACK` | 165 |

## Problem Coverage

| Problem | Rows |
|---|---:|
| `binary-search` | 90 |
| `climbing-stairs` | 90 |
| `longest-substring` | 90 |
| `lru-cache` | 90 |
| `matrix-diagonal-sum` | 90 |
| `merge-sorted-array` | 90 |
| `palindrome` | 90 |
| `reverse-linked-list` | 90 |
| `roman-to-integer` | 90 |
| `trapping-rain-water` | 90 |
| `two-sum` | 90 |
| `valid-parentheses` | 90 |

## Provenance

`target_feedback_action` is the supervised label from the problem-suite review rubric. `feedback_action` is the action returned by the running application through the real HTTP endpoint. This dataset is a controlled technical pilot dataset, not a semester-long classroom dataset.
