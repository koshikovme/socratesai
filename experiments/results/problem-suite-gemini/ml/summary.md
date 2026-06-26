# Problem Suite HTTP Benchmark - ML

- Generated at: 2026-05-21T08:37:25.428109+00:00
- Environment: Docker Compose backend, PostgreSQL, ML policy, Gemini 2.5 Flash-Lite
- Mode: `ML`
- Programming problems: 12
- Cohorts: 10

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 720 | 708 | 12 | 90.11% | 89.40% | 1531.11 ms | 2873 ms | 0.5 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 196 | 198 |
| `CONCEPTUAL_HINT` | 278 | 266 |
| `GUIDING_QUESTION` | 116 | 116 |
| `NO_FEEDBACK` | 118 | 128 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 93.94% | 94.90% | 94.42% | 196 |
| `CONCEPTUAL_HINT` | 92.86% | 88.85% | 90.81% | 278 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 116 |
| `NO_FEEDBACK` | 69.53% | 75.42% | 72.36% | 118 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 60 | 100.00% |
| `climbing-stairs` | 60 | 83.33% |
| `longest-substring` | 57 | 98.25% |
| `lru-cache` | 58 | 51.72% |
| `matrix-diagonal-sum` | 58 | 100.00% |
| `merge-sorted-array` | 57 | 98.25% |
| `palindrome` | 60 | 100.00% |
| `reverse-linked-list` | 59 | 100.00% |
| `roman-to-integer` | 60 | 100.00% |
| `trapping-rain-water` | 59 | 100.00% |
| `two-sum` | 60 | 100.00% |
| `valid-parentheses` | 60 | 50.00% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
