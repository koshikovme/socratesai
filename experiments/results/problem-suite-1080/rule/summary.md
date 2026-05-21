# Problem Suite HTTP Benchmark - RULE

- Generated at: 2026-05-19T11:42:13.771727+00:00
- Environment: Real HTTP Spring Boot process, PostgreSQL 16.11 Docker Compose database, 12-problem programming suite, 15 cohorts
- Mode: `RULE`
- Programming problems: 12
- Cohorts: 15

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1080 | 1080 | 0 | 87.50% | 85.90% | 256.77 ms | 315 ms | 3.89 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 300 | 300 |
| `CONCEPTUAL_HINT` | 420 | 435 |
| `GUIDING_QUESTION` | 180 | 180 |
| `NO_FEEDBACK` | 180 | 165 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.00% | 95.00% | 95.00% | 300 |
| `CONCEPTUAL_HINT` | 86.21% | 89.29% | 87.72% | 420 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 180 |
| `NO_FEEDBACK` | 63.64% | 58.33% | 60.87% | 180 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 90 | 100.00% |
| `climbing-stairs` | 90 | 83.33% |
| `longest-substring` | 90 | 100.00% |
| `lru-cache` | 90 | 50.00% |
| `matrix-diagonal-sum` | 90 | 100.00% |
| `merge-sorted-array` | 90 | 100.00% |
| `palindrome` | 90 | 83.33% |
| `reverse-linked-list` | 90 | 100.00% |
| `roman-to-integer` | 90 | 100.00% |
| `trapping-rain-water` | 90 | 83.33% |
| `two-sum` | 90 | 100.00% |
| `valid-parentheses` | 90 | 50.00% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
