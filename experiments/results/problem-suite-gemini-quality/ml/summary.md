# Problem Suite HTTP Benchmark - ML

- Generated at: 2026-05-21T10:12:26.201578+00:00
- Environment: Docker Compose backend on 18080, PostgreSQL, ML policy, Gemini 2.5 Flash-Lite, feedback text captured
- Mode: `ML`
- Programming problems: 12
- Cohorts: 2

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 144 | 144 | 0 | 90.28% | 89.48% | 1653.17 ms | 4654 ms | 0.6 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 40 | 40 |
| `CONCEPTUAL_HINT` | 56 | 54 |
| `GUIDING_QUESTION` | 24 | 24 |
| `NO_FEEDBACK` | 24 | 26 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.00% | 95.00% | 95.00% | 40 |
| `CONCEPTUAL_HINT` | 92.59% | 89.29% | 90.91% | 56 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 24 |
| `NO_FEEDBACK` | 69.23% | 75.00% | 72.00% | 24 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 12 | 100.00% |
| `climbing-stairs` | 12 | 83.33% |
| `longest-substring` | 12 | 100.00% |
| `lru-cache` | 12 | 50.00% |
| `matrix-diagonal-sum` | 12 | 100.00% |
| `merge-sorted-array` | 12 | 100.00% |
| `palindrome` | 12 | 100.00% |
| `reverse-linked-list` | 12 | 100.00% |
| `roman-to-integer` | 12 | 100.00% |
| `trapping-rain-water` | 12 | 100.00% |
| `two-sum` | 12 | 100.00% |
| `valid-parentheses` | 12 | 50.00% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
