# Problem Suite HTTP Benchmark - ML

- Generated at: 2026-05-21T10:26:51.213707+00:00
- Environment: Docker Compose backend on 18080, PostgreSQL, fixed ML policy API contract, fresh student ids, Gemini 2.5 Flash-Lite, feedback text captured
- Mode: `ML`
- Programming problems: 12
- Cohorts: 5

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 360 | 360 | 0 | 95.83% | 95.80% | 1079.21 ms | 2072 ms | 0.93 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 100 | 105 |
| `CONCEPTUAL_HINT` | 140 | 145 |
| `GUIDING_QUESTION` | 60 | 60 |
| `NO_FEEDBACK` | 60 | 50 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.24% | 100.00% | 97.56% | 100 |
| `CONCEPTUAL_HINT` | 93.10% | 96.43% | 94.74% | 140 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 60 |
| `NO_FEEDBACK` | 100.00% | 83.33% | 90.91% | 60 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 30 | 100.00% |
| `climbing-stairs` | 30 | 100.00% |
| `longest-substring` | 30 | 100.00% |
| `lru-cache` | 30 | 66.67% |
| `matrix-diagonal-sum` | 30 | 100.00% |
| `merge-sorted-array` | 30 | 100.00% |
| `palindrome` | 30 | 100.00% |
| `reverse-linked-list` | 30 | 100.00% |
| `roman-to-integer` | 30 | 100.00% |
| `trapping-rain-water` | 30 | 100.00% |
| `two-sum` | 30 | 100.00% |
| `valid-parentheses` | 30 | 83.33% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
