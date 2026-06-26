# Codeforces Auxiliary Policy Dataset

- Output: `C:\Users\Koshikov\Desktop\socratesai\ml\codeforces_policy_dataset.csv`
- Rows: 60564
- Problems: 9183
- Languages: 18

## Source Rows

| Source | Rows |
|---|---:|
| `selected_accepted` | 42467 |
| `selected_incorrect` | 18097 |

## Target Distribution

| Target action | Rows |
|---|---:|
| `NO_FEEDBACK` | 42467 |
| `CONCEPTUAL_HINT` | 15016 |
| `CODE_HIGHLIGHT` | 2111 |
| `GUIDING_QUESTION` | 970 |

## Verdict Distribution

| Verdict | Rows |
|---|---:|
| `OK` | 42467 |
| `WRONG_ANSWER` | 8961 |
| `TIME_LIMIT_EXCEEDED` | 6276 |
| `RUNTIME_ERROR` | 1672 |
| `SKIPPED` | 715 |
| `MEMORY_LIMIT_EXCEEDED` | 404 |
| `COMPILATION_ERROR` | 34 |
| `CHALLENGED` | 25 |
| `IDLENESS_LIMIT_EXCEEDED` | 9 |
| `CRASHED` | 1 |

## Interpretation Boundary

This is an auxiliary verdict-derived policy dataset. It uses real human programming submissions, but its target actions are weak labels derived from judge verdicts and passed-test counts, not instructor feedback labels.
