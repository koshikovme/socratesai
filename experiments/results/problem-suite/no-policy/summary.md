# Problem Suite HTTP Benchmark - NO_POLICY

- Generated at: 2026-05-18T05:59:46.839870+00:00
- Environment: Real HTTP Spring Boot process, PostgreSQL 16.11 Docker Compose database, fixed no-policy baseline, 12-problem programming suite
- Mode: `NO_POLICY`
- Programming problems: 12
- Cohorts: 4

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 288 | 288 | 0 | 38.89% | 14.00% | 47.34 ms | 63 ms | 21.12 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 80 | 0 |
| `CONCEPTUAL_HINT` | 112 | 288 |
| `GUIDING_QUESTION` | 48 | 0 |
| `NO_FEEDBACK` | 48 | 0 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 0.00% | 0.00% | 0.00% | 80 |
| `CONCEPTUAL_HINT` | 38.89% | 100.00% | 56.00% | 112 |
| `GUIDING_QUESTION` | 0.00% | 0.00% | 0.00% | 48 |
| `NO_FEEDBACK` | 0.00% | 0.00% | 0.00% | 48 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 24 | 33.33% |
| `climbing-stairs` | 24 | 50.00% |
| `longest-substring` | 24 | 33.33% |
| `lru-cache` | 24 | 50.00% |
| `matrix-diagonal-sum` | 24 | 33.33% |
| `merge-sorted-array` | 24 | 33.33% |
| `palindrome` | 24 | 33.33% |
| `reverse-linked-list` | 24 | 50.00% |
| `roman-to-integer` | 24 | 33.33% |
| `trapping-rain-water` | 24 | 50.00% |
| `two-sum` | 24 | 33.33% |
| `valid-parentheses` | 24 | 33.33% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
