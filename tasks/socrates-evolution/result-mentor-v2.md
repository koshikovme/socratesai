# Native AI hints, starter programs and workspace correction

Date: **2026-11-05**. Follow-up to production v0.9.1, now **deployed and verified** at
**https://socratesai.duckdns.org**. Release **v0.9.3**, source
`c53bf74f3b9e6a7be622f4e2a475dc9ff52fa9eb`, passed
[release/publication/automatic promotion](https://github.com/koshikovme/socratesai/actions/runs/41961137181).
The exact application digests, unchanged model checksums and separately certified executor
image/archive are recorded in [the permanent manifest](../../deploy/releases/v0.9.3.json).

The user's Java sample was a method-only `Solution` class. The dedicated provider reported
that `Main` could not be loaded; compilation success did not imply the program could start.
Separately, `NativeMentorService` generated AI wording only for ML-mode requests with known
execution evidence. Java/C++ were forced through RULE and never reached Gemini. Before a run,
the template only asked for samples. These were confirmed implementation gaps.

## Behavior

- Ask mentor works from the current code and public task statement/input/output description
  before any sample run. No implicit compile/run occurs, and unknown execution stays null.
- Gemini produces structured, validated `hint`, `mentorState` and `action` output for all
  supported languages. Python uses its eligible, confident trained classification as context.
  Java/C++ and unavailable/low-confidence Python classification use Gemini's estimated focus;
  they are never sent to the Python-trained model and have no fabricated confidence score.
- Native students receive no RULE/template fallback. Provider/allowance failure returns a
  standardized safe error; it does not pretend that a vague template came from Gemini.
  Historical administrator-only research policy/ablation routes remain compatible.
- Hints contain one or two short sentences tied to code, without a complete corrected solution.
  Hidden tests, expected outputs, reference solutions, provider diagnostics and backend secrets
  never enter the prompt. Source/prompt instructions are untrusted data; excerpts are labeled.
- Opening a starter and an already accepted complete graded submission do not consume AI
  allowance. Automatic requests wait for a 1.5-second typing pause and can be switched off.
  Explicit Ask mentor still works; no automatic REST retry spends again after a provider error.
- Durable monthly allowance remains 3,000 hints, at most 2,048 input / 128 output tokens each,
  conservative **$2.8032/month** at the reviewed prices. Per-minute/student-day caps are 60/30;
  inference concurrency is eight. Taxes, price changes and manual calls outside this app remain
  outside that estimate. No new service purchase or model training was performed.
- Empty authored starter fields now project runnable Java/Python/C++ stdin/stdout scaffolds
  with an unfinished solve body. Nonblank teacher-authored scaffolds remain exact. Existing
  student code is never overwritten automatically; Reset to starter asks before replacing only
  the selected language buffer and advances its revision.
- `/problems/{id}` explains the full-program contract, Java Main/main/solve, standard I/O,
  optional samples and the difference between AI advice and judged correctness. The guide
  sits beside the editor. Runtime failures no longer show a green Compiled badge.
- Header names/roles are shown in Profile instead; compact navigation, accessible theme icon,
  consistent buttons, readable evidence/focus and removal of internal session IDs reduce clutter.

## Verification

- Local `mvnw verify`: **319 tests**, zero failures/errors, one opt-in experiment skipped;
  both coverage gates pass. Disposable PostgreSQL/Testcontainers only; no DB 21/22 access.
- Frontend: **93 tests**, ESLint, production build and **2 Chromium protocol-fixture journeys**
  passed. Coverage includes manual Ask before Run, auto-off behavior, errors without duplicate
  paid retries, per-language reset preservation, runtime badge and compact authorized navigation.
- Real Gemini generateContent JSON-schema probe: valid structured response, STOP, 363 input /
  52 output tokens, hint identifies Main from method-only Java source before execution.
  This small manual operator probe is outside application quota counters, not a performance study.
- Real dedicated Judge0 probes: Java, Python and C++ starters compiled/launched successfully,
  produced no problem answer and did not run on the workstation/application host/CI runner.
- Prompt schema, bounds, hostile code-as-data, safe provider failure, native classifier selection,
  current-source evidence, identity/ownership and export compatibility have regression coverage.

The implementation follows Google's [structured-output contract](https://aistudio.google.com/docs/structured-output?generate-content=true)
through the existing generateContent endpoint. Neither a Gemini estimated focus nor a source-only
ML prediction is a judge verdict. Accepted-derived progress and private diagnostic boundaries
are unchanged. Saved models, research metrics, datasets and papers were not rewritten; new
interaction provenance is `native-ai-v2`, distinct from the earlier native rule configuration.

## Completed production acceptance

- The main and tagged release workflows both passed on `c53bf74`; the latter published images
  and promoted the exact three recorded GHCR digests. All four application services are healthy,
  the schema remains V13 and the loaded model SHA-256 values match the unchanged manifest.
- `scripts/delivery/live_browser.cjs` passed on actual HTTPS/WSS/Monaco: a Java method-only
  buffer received a Gemini entry-point hint **before any Run/Submit POST**. The compact header,
  confirmed starter reset, unfinished starter's WRONG_ANSWER, language buffer isolation,
  accepted public run, accepted complete submission and logout passed. One WSS connection,
  four mentor messages, zero STOMP errors and zero unexpected page errors were observed.
  Two editor-cancellation notifications were separately counted during disposal/language changes.
- `live_acceptance.py --run-label v0.9.3` obtained fresh ACCEPTED runs/submissions and
  idempotent replay for Python, Java and C++; accepted-derived progress and hidden-safe
  projections passed. Foreign submission/teacher access, elevated public signup and anonymous/
  foreign WSS subscriptions were denied. A spoofed body studentId was ignored in favor of the
  authenticated principal. An echo submission remained WRONG_ANSWER without hidden output.
- Additional code-only requests showed actual Python **ML classification + Gemini wording**,
  and C++ **Gemini classification + wording**, with `executionKnown=false`. The Python model's
  estimated label is not evidence that a source-only classification is correct; no new model
  accuracy result is claimed. Gemini classification retains a null confidence score.
- `live_capacity.py` received **30 genuine code-only Gemini hints over 30 authenticated WSS
  sessions**, then **30 ACCEPTED Python sample runs**. Zero graded attempts were created;
  load-account progress stayed unsolved. POST 202 p95 was **3,159.2 ms**, maximum completion
  **37.1 seconds**. This is a bounded synthetic single-public-sample rehearsal, not a classroom
  SLA or throughput promise for arbitrary compiler-heavy workloads.
- The patched dedicated executor preserved database/Redis volumes. Actual Python/Java/C++
  solutions passed; all three unfinished starters compiled/launched without printing the
  answer. Nine additional probes exercised syntax errors, CPU/output/process/memory/compiler
  bounds and filesystem/environment/network isolation, exclusively on that VM.
- Post-check health and disk checks passed. A fresh encrypted application backup completed
  and off-host encrypted copies passed checksum verification. Selected last-30-minute logs
  across all eight containers had zero known credential/source-fixture matches and zero JWT
  matches. This is a bounded check, not a claim that arbitrary secrets can never be logged.

Protected JSON/screenshots, synthetic identities and recovery material remain under ignored
`.delivery/`; no source, provider ticket, credential or interaction export was committed.
Earlier restore, lease-recovery and compatible-rollback evidence remains dated in
[result-9-production.md](result-9-production.md); those interruptions were not repeated merely
to change the hint UI. Long-term graded-source retention and external incident notification
routing remain explicit owner follow-ups.

## Security gate correction before release

The v0.9.2 workflow passed backend/frontend/model/infrastructure and image startup, then
correctly blocked publication on CVE-2026-109518 in APT 2.6.1 / libapt-pkg6.0 in the policy
and execution images. The original report artifact was fetched and its SHA-256 verified
against GitHub metadata; cached operator scans were not used to waive the finding.

Debian's [official tracker](https://security-tracker.debian.org/tracker/CVE-2026-109518)
identifies 2.6.2 as the Bookworm security fix. Both image recipes now install that exact
version over HTTPS, without changing compiler/model versions. All four rebuilt images
passed the fixed HIGH/CRITICAL gate with a new Trivy cache. The stock PostgreSQL helper
findings remain separately reported under the existing exposure review.

`scan_summary.py` now prints only advisory/package/fixed-version data, never image env,
and fails closed on a missing/malformed report. Six delivery tests pass, including this
report safety and failure behavior. v0.9.2 was not deployed or retagged. The corrected
v0.9.3 release passed the complete GitHub Actions gate and automatic promotion. The dedicated
executor now runs the checksum-verified `sha256:c2853099…` local image with APT 2.6.2;
the exact full identifier and archive SHA-256 are in the release manifest. Compiler/catalog
versions and model artifacts did not change.
