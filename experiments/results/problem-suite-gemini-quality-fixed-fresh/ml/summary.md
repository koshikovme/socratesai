# Problem Suite HTTP Benchmark - ML

- Generated at: 2026-05-21T10:20:03.632255+00:00
- Environment: Docker Compose backend on 18080, PostgreSQL, fixed ML policy API contract, fresh student ids, Gemini 2.5 Flash-Lite, feedback text captured
- Mode: `ML`
- Programming problems: 12
- Cohorts: 1

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 72 | 72 | 0 | 95.83% | 95.80% | 1444.25 ms | 4296 ms | 0.69 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 20 | 21 |
| `CONCEPTUAL_HINT` | 28 | 29 |
| `GUIDING_QUESTION` | 12 | 12 |
| `NO_FEEDBACK` | 12 | 10 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.24% | 100.00% | 97.56% | 20 |
| `CONCEPTUAL_HINT` | 93.10% | 96.43% | 94.74% | 28 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 12 |
| `NO_FEEDBACK` | 100.00% | 83.33% | 90.91% | 12 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 6 | 100.00% |
| `climbing-stairs` | 6 | 100.00% |
| `longest-substring` | 6 | 100.00% |
| `lru-cache` | 6 | 66.67% |
| `matrix-diagonal-sum` | 6 | 100.00% |
| `merge-sorted-array` | 6 | 100.00% |
| `palindrome` | 6 | 100.00% |
| `reverse-linked-list` | 6 | 100.00% |
| `roman-to-integer` | 6 | 100.00% |
| `trapping-rain-water` | 6 | 100.00% |
| `two-sum` | 6 | 100.00% |
| `valid-parentheses` | 6 | 83.33% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
