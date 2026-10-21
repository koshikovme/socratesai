# Judge0 adapter: configuration and execution boundary

The backend contains a Judge0-compatible HTTP adapter, not an execution host. It never compiles or runs student source locally. `APP_JUDGE0_ENABLED` defaults to `false`; there is no configured production endpoint or approved language list. The local integration tests emulate HTTP responses without running submitted programs.

## Application integration

`execution.domain.port.CodeExecutionPort` exposes two asynchronous operations:

1. `submitBatch(ExecutionBatch)` validates and snapshots trusted cases, verifies the configured runtime against `GET /languages`, and sends one `POST /submissions/batch?base64_encoded=true`. It returns `SubmittedExecutionBatch` with case/token associations and the effective absolute deadline.
2. Persist those tickets with their job/generation before calling `pollBatch(SubmittedExecutionBatch)`. Polling requests only unfinished tokens, preserves completed results, and matches responses by token. Provider order never determines case identity. Use `StudentExecutionResultMapper` for student responses.

The future Step 5 worker must construct requests from immutable database versions, enforce user/job admission limits, split suites into bounded batches, persist tickets, and aggregate the complete required suite. An accepted adapter batch is not acceptance of a larger suite. This change does not add controllers, dispatch database jobs, or connect execution to WebSockets/ML.

Create requests are never automatically retried. A partial/malformed creation response, transport failure, server failure, or timeout after dispatch is an uncertain remote outcome: `SandboxException.mayHaveBeenSubmitted=true`. Do not interpret it as permission to resubmit. A definitive HTTP 4xx rejection except 408 has `mayHaveBeenSubmitted=false`. Partially returned tickets are not exposed as successful batches; remote work may remain until provider limits/retention remove it. Job reconciliation and this uncertain state must be handled before enabling public submissions.

Only idempotent polling retries HTTP 429/5xx and transport failures. Backoff is `min(initial * 2^attempt, maximum)`; the attempt count also bounds pending-status polls. The original deadline is retained across creation and polling, and is capped by the operator's operation timeout. An independent scheduler cancels local HTTP work even if a response body stalls. Cancellation/deadline stop local I/O; they do not prove remote process termination. Judge0 must enforce its execution limits and cleanup independently. No sleep or synchronous network wait runs on application request threads.

## Operator configuration

Configure these values through Spring external configuration or environment variables. Keep credentials outside Git and chat. An enabled adapter requires an authentication header/key and a nonempty approved runtime list. The following external YAML uses deliberately unresolved operator values:

```yaml
app:
  execution:
    judge0:
      enabled: true
      base-url: ${APP_JUDGE0_BASE_URL}
      auth-header: ${APP_JUDGE0_AUTH_HEADER:X-Auth-Token}
      api-key: ${APP_JUDGE0_API_KEY}
      runtimes:
        - key: ${APP_JUDGE0_RUNTIME_KEY}
          language-key: ${APP_JUDGE0_LANGUAGE_KEY}
          language-id: ${APP_JUDGE0_LANGUAGE_ID}
          version: ${APP_JUDGE0_RUNTIME_VERSION}
          provider-name: ${APP_JUDGE0_RUNTIME_PROVIDER_NAME}
```

Use a private authenticated endpoint with HTTPS. Plain HTTP is accepted for a loopback protocol test; a private HTTP deployment requires the explicit `APP_JUDGE0_ALLOW_PLAIN_HTTP=true` operator setting and a separately justified transport boundary. Redirects are never followed. URLs with embedded credentials, query strings or fragments are rejected. Source cannot supply endpoints, headers, callbacks, compiler flags, images or attachments.

Runtime IDs are operator values from the chosen server's catalog, not public-demo constants. Before any source is submitted, the adapter verifies exact ID and full catalog name against the configured language key/runtime key/version. Catalog freshness defaults to one minute. A missing or changed runtime fails closed. Entry-point conventions remain a publication/runtime validation responsibility: one source file, stdin/stdout, and the chosen compiler's entry point; they must be tested against an actual runtime before advertising support.

Important defaults, all configurable under `app.execution.judge0` and `APP_JUDGE0_*` in `application.yaml`:

| Setting | Default |
| --- | --- |
| Connect / individual request / whole operation | 2 s / 5 s / 30 s |
| Initial / maximum backoff; poll attempts | 100 ms / 2 s; 100 |
| Cases per batch / active local operations | 5 / 16 |
| Response bytes, including chunked/error bodies | 2 MiB |
| Source UTF-8 bytes / existing character ceiling | 80,000 / 20,000 |
| Each stdin/expected-output UTF-8 value | 65,536 bytes |
| Processes/threads requested from provider | 64 |

These are bounded implementation defaults, not measured production capacity. The HTTP executor and its queue are bounded. The polling scheduler holds at most a deadline and pending retry per admitted operation; completion/cancellation removes timers and releases the slot. Submitting a batch releases the local slot once tickets return: this is not a global limit on remote jobs. Step 5 must enforce outstanding-job quotas.

CPU/wall milliseconds are converted to seconds; memory stays in KiB; process/thread and aggregate process memory/time settings are explicit; networking is disabled in each request; stderr is separate; execution runs once. Provider file size is `ceil(outputLimitBytes / 1024)` KiB, at least one KiB. Decoded stdout/stderr are also capped at the exact byte limit; compile diagnostics are capped at 64 KiB. Limits are bounded by `ContesterLimits`. Compile-specific CPU/memory/file limits, real filesystem/network denial, supported process/thread counts and cleanup require configuration and verification on the execution host. Request flags alone do not establish isolation. Do not raise provider limits to make an unverified runtime appear supported.

## Status and comparison contracts

| Judge0 status ID | Internal result |
| --- | --- |
| 1 / 2 | `QUEUED` / `RUNNING`, no verdict |
| 3 | `FINISHED`; local text comparison decides `ACCEPTED` or `WRONG_ANSWER` |
| 4 / 5 / 6 | `WRONG_ANSWER` / `TIME_LIMIT_EXCEEDED` / `COMPILATION_ERROR` |
| 7, 9–12, 14 | `RUNTIME_ERROR` |
| 8 (SIGXFSZ) | `OUTPUT_LIMIT_EXCEEDED` |
| 13 | `SYSTEM_ERROR`, provider internal failure |
| Missing/unknown status, malformed metrics/protocol | `SYSTEM_ERROR`, no student verdict |

Judge0 CE has no dedicated memory-limit status in the documented status table. Only measured usage above the requested memory limit can produce `MEMORY_LIMIT_EXCEEDED`; authoritative time/compilation/file-limit statuses retain their verdict. An OOM kill with no over-limit measurement remains `RUNTIME_ERROR`. Student-controlled stderr/status-description text never determines verdict. Decoded output overflow produces output-limit failure for normal/WA results; compiler/runtime/time failures retain their authoritative verdict. Invalid UTF-8 normal output is a wrong answer; malformed Base64 is a protocol failure. Metrics are nullable; seconds round up to milliseconds and KiB convert to bytes with overflow checks.

Both comparison policies normalize CRLF to LF. `EXACT` then compares every character, including final LF. `IGNORE_TRAILING_LINE_ENDINGS` removes only final LF characters from both values. Lone CR, internal empty lines, spaces, tabs, case and Unicode code points remain significant. Neither policy trims whitespace or uses locale-sensitive comparison.

`ExecutionStatus` describes progress/infrastructure; `ExecutionVerdict` describes a completed student result. Empty/incomplete/invalid batches cannot become accepted. Aggregation selects the first failing verdict in trusted case order only after the complete batch finishes. Any operation failure produces overall `SYSTEM_ERROR` even when some case results are already available.

## Privacy and host validation

Expected outputs stay in the backend and are never included in create requests. Hidden stdout/stderr/compile output are discarded after internal comparison. The student mapper omits hidden cases completely, including per-case IDs, timing, memory and diagnostics. Only aggregate status/verdict/error and public sample details are returned. Do not serialize domain/provider objects from controllers or persist hidden diagnostics through an alternative mapper.

Source, stdin, expected output, provider tokens and diagnostics are excluded from generated `toString()` methods. Adapter exceptions contain fixed codes with no provider bodies or original causes. ML failure logging likewise omits response bodies and exception messages. JDK HTTP wire tracing is rejected when the adapter is enabled; access proxies, APM and HTTP interception must also avoid bodies, auth headers and token-bearing query strings. Do not enable packet/body tracing for student traffic.

Use a dedicated execution VM/trust zone without application credentials, database connectivity, repository/host mounts or Docker socket access from the backend. Review the chosen image's provenance/digest, kernel/cgroup/privilege prerequisites and current advisories. Upstream recorded critical escape fixes in the 1.13.1 release; this historical version is not a blanket approval of an image today. A managed compatible API needs equivalent retention/region/auth/limit checks. Primary references: [API and status table](https://ce.judge0.com/), [release notes](https://github.com/judge0/judge0/releases), [security advisories](https://github.com/judge0/judge0/security/advisories).

Before enabling student execution, finish work-notes P3.1/2/4/6/8/9/10/11: approve the server/digest/runtime catalog and retention policy; verify every advertised language and resource/network/filesystem boundary in a disposable sandbox; measure overhead; and implement durable Step 5 orchestration. No such host was available for this delivery.

## Local verification

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
  bash mvnw -B verify
```

The suite exercises real localhost HTTP framing with JDK `HttpServer` and uses the existing disposable PostgreSQL Testcontainers tests for schema regression. It does not connect to a real Judge0 API, run student programs, or test shared databases 21/22. Backend legacy `SyntaxCheckEngine` now supplies bounded static estimates only, tagged `compilerExecuted=false`, `testsExecuted=false`, `evidenceOrigin=STATIC_HEURISTIC`. Existing ML feature names/log fields remain compatible; their execution-evidence semantics must be migrated in Step 6.
