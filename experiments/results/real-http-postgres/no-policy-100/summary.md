# Real HTTP PostgreSQL Benchmark - NO_POLICY

- Generated at: 2026-05-15T18:19:40.071782+00:00
- Environment: Real HTTP Spring Boot process, PostgreSQL 16.11 Docker Compose database, fixed no-policy baseline
- Mode: `NO_POLICY`

## Replay

| Requests | Successful | Errors | Wall time | Throughput | Mean | Median | P95 | P99 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 100 | 0 | 4797 ms | 20.85 req/s | 47.95 ms | 46.0 ms | 64 ms | 294 ms |

## Stress

- Concurrency: 8

| Requests | Successful | Errors | Wall time | Throughput | Mean | Median | P95 | P99 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 0 ms | 0 req/s | 0 ms | 0 ms | 0 ms | 0 ms |

## Action Distribution

| Action | Replay events |
|---|---:|
| `CONCEPTUAL_HINT` | 100 |

## Interpretation Boundary

This benchmark measures local real-HTTP prototype behavior with PostgreSQL. It is not a classroom learning-outcome study.
