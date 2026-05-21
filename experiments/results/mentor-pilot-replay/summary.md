# Mentor Pilot Replay Results

- Generated at: 2026-05-15T22:01:47.226667+05:00
- Environment: Spring Boot test profile, H2 in-memory database, template feedback, rule policy, MockMvc request pipeline.
- Replay events: 570
- Replay wall time: 132873 ms
- Replay throughput: 4.29 events/s

## Latency

| Metric | Mean | P95 | P99 |
|---|---:|---:|---:|
| HTTP-style wall latency, ms | 232.51 | 307 | 334 |
| Logged total service latency, ms | 224.93 | 296 | 315 |
| Analyzer latency, ms | 224.91 | 296 | 315 |
| Policy latency, ms | 0.01 | 0 | 1 |
| Feedback latency, ms | 0.01 | 0 | 0 |

## Action Distribution

| Action | Events | Share |
|---|---:|---:|
| CODE_HIGHLIGHT | 90 | 15.79% |
| CONCEPTUAL_HINT | 330 | 57.89% |
| GUIDING_QUESTION | 90 | 15.79% |
| NO_FEEDBACK | 60 | 10.53% |

## Error-Type Distribution

| Error type | Events | Share |
|---|---:|---:|
| OFF_BY_ONE | 120 | 21.05% |
| STUCK_NO_PROGRESS | 90 | 15.79% |
| SYNTAX_ERROR | 150 | 26.32% |
| UNKNOWN | 120 | 21.05% |
| WRONG_CONDITION | 90 | 15.79% |

## Concurrent Stress Smoke Test

| Requests | Concurrency | Errors | Wall time | Mean latency | P95 latency | P99 latency |
|---:|---:|---:|---:|---:|---:|---:|
| 400 | 8 | 0 | 22545 ms | 446.43 ms | 707 ms | 771 ms |

## Interpretation Boundary

These results measure reproducible operational behavior of the prototype. They do not measure learning gain, retention, or authentic student outcome improvement.
