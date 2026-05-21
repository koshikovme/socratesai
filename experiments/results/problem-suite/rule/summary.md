# Problem Suite HTTP Benchmark - RULE

- Generated at: 2026-05-18T05:57:09.807202+00:00
- Environment: Real HTTP Spring Boot process, PostgreSQL 16.11 Docker Compose database, 12-problem programming suite
- Mode: `RULE`
- Programming problems: 12
- Cohorts: 4

## Overall

| Events | Successful | Errors | Agreement | Macro F1 | Mean latency | P95 latency | Throughput |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 288 | 288 | 0 | 87.50% | 85.90% | 47.66 ms | 62 ms | 20.97 req/s |

## Action Distribution

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 80 | 80 |
| `CONCEPTUAL_HINT` | 112 | 116 |
| `GUIDING_QUESTION` | 48 | 48 |
| `NO_FEEDBACK` | 48 | 44 |

## Per-Class Metrics

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.00% | 95.00% | 95.00% | 80 |
| `CONCEPTUAL_HINT` | 86.21% | 89.29% | 87.72% | 112 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 48 |
| `NO_FEEDBACK` | 63.64% | 58.33% | 60.87% | 48 |

## Problem Coverage

| Problem | Events | Agreement |
|---|---:|---:|
| `binary-search` | 24 | 100.00% |
| `climbing-stairs` | 24 | 83.33% |
| `longest-substring` | 24 | 100.00% |
| `lru-cache` | 24 | 50.00% |
| `matrix-diagonal-sum` | 24 | 100.00% |
| `merge-sorted-array` | 24 | 100.00% |
| `palindrome` | 24 | 83.33% |
| `reverse-linked-list` | 24 | 100.00% |
| `roman-to-integer` | 24 | 100.00% |
| `trapping-rain-water` | 24 | 83.33% |
| `two-sum` | 24 | 100.00% |
| `valid-parentheses` | 24 | 50.00% |

## Interpretation Boundary

This benchmark is a controlled programming problem-suite evaluation. It measures behavior on task-level code states and review-rubric action labels; it should be paired with a real classroom protocol before making learning-gain claims.
