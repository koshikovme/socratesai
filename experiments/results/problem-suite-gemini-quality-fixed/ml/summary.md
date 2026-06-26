# Problem Suite HTTP Benchmark - ML

- Generated at: 2026-05-21T10:16:40.046306+00:00
- Environment: Docker Compose backend on 18080, PostgreSQL, fixed ML policy API contract, Gemini 2.5 Flash-Lite, feedback text captured
- Mode: `ML`
- Programming problems: 12
- Cohorts: 1

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 72 | 72 | 0 | 51.39% | 50.76% | 1334.01 ms | 3832 ms | 0.75 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 20 | 0 |
| `CONCEPTUAL_HINT` | 28 | 17 |
| `GUIDING_QUESTION` | 12 | 12 |
| `NO_FEEDBACK` | 12 | 43 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 0.00% | 0.00% | 0.00% | 20 |
| `CONCEPTUAL_HINT` | 88.24% | 53.57% | 66.67% | 28 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 12 |
| `NO_FEEDBACK` | 23.26% | 83.33% | 36.36% | 12 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 6 | 50.00% |
| `climbing-stairs` | 6 | 66.67% |
| `longest-substring` | 6 | 50.00% |
| `lru-cache` | 6 | 33.33% |
| `matrix-diagonal-sum` | 6 | 50.00% |
| `merge-sorted-array` | 6 | 50.00% |
| `palindrome` | 6 | 50.00% |
| `reverse-linked-list` | 6 | 66.67% |
| `roman-to-integer` | 6 | 50.00% |
| `trapping-rain-water` | 6 | 66.67% |
| `two-sum` | 6 | 50.00% |
| `valid-parentheses` | 6 | 33.33% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
