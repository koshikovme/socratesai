# Native AI hints, starter programs and workspace correction

Date: **2026-11-05**. Requested follow-up to production v0.9.1. Deployment verification is
in progress; this report does not yet claim the new application is live.

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

## Release verification remaining

Record the immutable v0.9.2 image manifest and workflow outcome, then run the real browser
journey showing a Java Gemini hint before any execution, starter reset and real judging,
plus ownership/hidden-data checks and a bounded 30-session AI/sample rehearsal.
