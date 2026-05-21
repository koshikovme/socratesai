# Problem Suite Policy Comparison

| Mode | Events | Agreement | Macro F1 | Mean latency | P95 latency |
|---|---:|---:|---:|---:|---:|
| `RULE` | 288 | 87.50% | 85.90% | 47.66 ms | 62 ms |
| `ML` | 288 | 87.50% | 85.90% | 48.19 ms | 65 ms |
| `NO_POLICY` | 288 | 38.89% | 14.00% | 47.34 ms | 63 ms |

## Per-Class F1

| Mode | CODE_HIGHLIGHT | CONCEPTUAL_HINT | GUIDING_QUESTION | NO_FEEDBACK |
|---|---:|---:|---:|---:|
| `RULE` | 95.00% | 87.72% | 100.00% | 60.87% |
| `ML` | 95.00% | 87.72% | 100.00% | 60.87% |
| `NO_POLICY` | 0.00% | 56.00% | 0.00% | 0.00% |

## Interpretation Boundary

The benchmark uses a controlled programming problem suite and review-rubric action labels. It is stronger than a narrow smoke test, but it is not a substitute for a classroom learning-gain study.
