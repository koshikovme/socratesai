# Step 7 — Native frontend and complete user journeys

Implemented on 2026-10-22 against backend commit `6c3bfca`. Scope: `socratesai-frontend/` and task documentation. Backend APIs, migrations, model artifacts, papers and saved research results were not changed.

## Implemented journeys

### Student catalog and progress

- Removed `leetcodeService.js`, the legacy task service and all external catalog calls/cache reads. Logout removes the old cache key for existing installations.
- `/dashboard` reads bounded native `/api/problems` and `/api/collections` pages. Page sizes are 10/20/50; requests are cancelled/fenced when switching pages, catalog type or account.
- Title/topic, difficulty and progress filters explicitly apply to the current page. **The existing backend supports `page,size` only; global server-side search/filtering was not added or implied.**
- SOLVED badges require `/api/users/me/progress` with `progressSource=JUDGED_SUBMISSION` and server status `SOLVED`. Mentor estimates cannot grant this state.
- `/collections/:id` preserves problem order and published version pins. Its progress uses accepted evidence for the exact version from `/api/collections/{id}/progress`.
- Profile links use native problem IDs; unknown execution metrics remain unknown and historical mentor activity retains its provenance label.

### Versioned solution workspace

`/problems/:id?version=<publishedVersionId>&collection=<collectionId>` separates statement/public samples, editor, mentor, judging result and paged owner submission history. The numeric legacy `/tasks/:id` route redirects to the native route; external IDs are not remapped to native IDs.

- Pinia buffers are memory-only and keyed by `(problemId, problemVersionId, languageKey)`. Starters initialize each context once, so language switching preserves edits.
- Each context has a UUID `editorSessionId` and monotonic safe-integer `codeRevision`. Returning to a previously sent context, manual refresh and transport recovery advance the revision as necessary. A new workspace mount renews session IDs.
- Typing and mentor requests never allocate attempts. The displayed attempt comes only from submission DTOs.
- Oversized source (>20,000 UTF-16 characters, matching the backend contract) is retained locally for editing but is not sent to judging or mentor endpoints.
- At most 24 source contexts are retained; capacity never silently overwrites a draft. An explicit confirmation discards other buffers. Unsaved navigation/reload is guarded; logout clears buffers without prompting.
- Monaco is loaded with the solver route, bundles a same-origin worker and Java/Python/C++ syntax definitions, and disposes each editor/model on context unmount. The catalog no longer eagerly loads Monaco.

### Mentor transport and correlation

| Operation | Contract |
| --- | --- |
| STOMP CONNECT | `Authorization: Bearer …` header; no token in URL |
| SUBSCRIBE | `/user/queue/feedback` only |
| SEND | `/app/code.update` |
| Snapshot | `problemId`, `problemVersionId`, `languageKey`, `editorSessionId`, `codeRevision`, full `code` |
| HTTP recovery | `POST /api/mentor/native-feedback`, same allowlisted body |
| Reply acceptance | Exact context/editor/revision plus SHA-256 of the current full UTF-8 source; checked again after async digest |

Client identity, attempt numbers, analyzer results and arbitrary context are not forwarded. Typing debounce is 350 ms. STOMP ERROR/malformed replies use fixed safe messages; raw frames and source are not logged. Connection state, ML/RULE, safe fallback reason and unknown/executed evidence are visible. The source label distinguishes WS and HTTP replies.

Transport instances belong to a mounted workspace. Reconnect and initial connection failure recover through the same revision stream; a missing WS reply triggers one HTTP recovery after 20 seconds, with a new revision. Initial connection fallback starts after 9 seconds. Obsolete HTTP work is aborted. Navigating to another problem/version recycles the socket to release server editor registrations; correlation metadata is bounded to 64 entries. HTTP-only editor registrations still obey the backend's TTL and admission limits, with visible capacity errors.

### Sample runs, submissions and history

- Run samples uses `POST /api/problems/{id}/runs`; Submit uses `/submissions` with the optional collection pin. Every new action generates a UUID idempotency key. Buttons lock while creating/judging.
- A lost POST response retains the exact original body/source/key. Explicit retry reuses them. Once an ID is known, recovery uses GET only; it never repeats the create call.
- Polling uses `/api/runs/{id}` or `/api/submissions/{id}`, starts at 500 ms and backs off up to 5 seconds, with a six-minute monitoring budget. Paused status checking can be resumed. `SYSTEM_ERROR` with `retryScheduled=true` remains pending.
- Expired/unavailable run results stop polling and allow a fresh action. Server errors do not produce fabricated verdicts. Owner history is paged and permits checking an existing submission after refresh.
- Results are labelled with language/version/revision and separate samples from graded submissions. Accepted samples never produce SOLVED.
- **Current backend run/submission status DTOs contain metric-only cases.** The UI deliberately displays only case kind, status/verdict, time and memory. It does not fabricate sample stdout/stderr. Hidden stdout/stderr, compiler diagnostics, reference code and provider fields are never rendered, even if injected into a response. Published sample inputs/expected outputs use the separate public problem projection.

### Teacher and administrator

- `/teacher` pages the authorized problem/collection catalogs. `/teacher/problems/new` and `/:id` support draft CRUD, clone-to-new-version, visibility, statement previews, ordered sample/hidden tests, allowlisted languages/starters, CPU/wall/memory/output limits, archive and deletion where permitted.
- Reference source is entered separately per configured language and kept transiently. Verification uses the backend's asynchronous `/verify` and owner-scoped verification status endpoints, with bounded polling and explicit resume.
- Publish remains disabled until the saved draft has a FINISHED/ACCEPTED proof for its exact version, every required check passed, and `validUntil` has not expired. Editing invalidates the client proof; backend publication remains authoritative and atomic.
- Collection editors select published versions, explicitly update older pins, add/remove members, reorder with keyboard-accessible buttons and save the whole order with `lockVersion`. Publish/archive/draft-delete are supported. A 409 preserves local edits and offers an explicit reload.
- `/admin/users` uses the existing protected role PATCH with user ID, expected current role, target role and audit reason. It does not invent a user-list or first-admin provisioning API. First-admin setup remains external backend configuration.

## Security and presentation

- Every protected navigation verifies the current account through `/api/users/me`; a forged cached role cannot grant a staff route. Backend ownership checks remain authoritative. An unavailable identity service shows a safe recovery page.
- Active-session HTTP 401 clears all Pinia stores, credentials, sockets, polling/debounce timers, reference/hidden author data and transient source; late responses are discarded. A 401 from an old account request cannot log out a newer account.
- Errors are mapped from known envelope codes to fixed messages. Database/proxy bodies, stack traces, source and STOMP frame details are not shown or logged.
- `SafeMarkdown` renders escaped Vue text and preserves whitespace. No raw HTML/`v-html` rendering is used. Sample data, feedback and code remain text.
- Conventional responsive styling extends the existing theme; no missing visual reference was invented. Labels, focus rings, status/alert regions, pagination and reorder controls support keyboard use. A 390 px browser viewport was checked for catalog overflow; this is not a formal accessibility certification.

## Validation evidence

Environment: Node **22.23.1**, npm **10.9.8**, Chrome via Playwright; no shared databases, paid LLMs, real student data or local execution of student programs.

Commands run from `socratesai-frontend/` with Node 22 on PATH:

| Command | Result |
| --- | --- |
| `npm ci` | Successful clean installation from the final lockfile using npm 10.9.8 |
| `npm test` | 83 tests in 19 files, all passed |
| `npm run test:coverage` | All 83 tests passed; lines **72.58%**, statements **68.75%**, functions **62.11%**, branches **65.60%** |
| `npm run build` | Successful production build; bundled editor worker and route chunks emitted |
| `npm run test:e2e` | 2 production-preview Chrome scenarios passed (8.4 s in the final run) |
| `npm audit --json --registry=https://registry.npmjs.org --fetch-retries=0 --fetch-timeout=20000` | 0 reported vulnerabilities at verification time |
| `git diff --check` | Passed |

Existing coverage thresholds (55% lines/statements/functions, 45% branches) were preserved. Coverage now includes all frontend services, stores, composables, components and views plus the route access guard; new modules were not excluded. It is measured coverage, not a claim of 100%. New reports go to ignored `coverage-native/`; historical tracked coverage artifacts were preserved.

Unit/component cases cover real Pinia buffers, revision/hash races, debounce/reconnect/HTTP recovery, ID allowlists, safe ERROR handling, old-account 401, cancellation/logout, oversized source, grading idempotency, retry-scheduled recovery, monitoring deadline, expired sample results, hidden-data allowlists, exact-version progress, teacher proof expiry/dirty/partial failure, ownership-aware routes and optimistic conflicts.

Browser fixtures exercise teacher creation and verification in all three languages → publication → pinned collection → student language switching → actual browser SockJS/STOMP framing → Run → Submit → history/progress → denied teacher route → logout, plus anonymous production deep links, narrow viewport and keyboard focus. HTTP and STOMP responses are deterministic **synthetic protocol fixtures**, not a real backend/Judge0 integration or runtime certification. Browser page errors were absent in the complete journey.

Local logs: `/tmp/socrates-step7-test.log`, `/tmp/socrates-step7-test-coverage.log`, `/tmp/socrates-step7-build.log`, `/tmp/socrates-step7-e2e.log`, `/tmp/socrates-step7-audit.json`. Playwright traces on failure/test results and generated builds remain ignored.

## Dependencies and remaining boundaries

- Updated compatible dependency resolutions (including Vue 3.5.44, Axios 1.20.0, Vite 8.3.5), upgraded Vitest/coverage to 4.1.12, added Playwright 1.64.1 and pinned the DOMPurify override to 3.4.17 to eliminate the vulnerable version pinned by Monaco. Monaco remains 0.55.1. Final lock installation and test/build checks passed; no `audit fix --force` was used.
- Vite reports a large solver/Monaco chunk (~3.74 MB, ~961 kB gzip). It is loaded on the solver route; this informational warning remains visible. No production performance capacity claim is made.
- SPA deep-link fallback was validated with Vite preview. Production reverse proxy, HTTPS, cache policy, CI/CD and actual hosting belong to Steps 8–9.
- Real reference execution/judging still requires the separate isolated Judge0 host already identified as unavailable. Browser fixtures establish frontend behavior, not successful execution of those programs.
- Process-local server admission/multi-instance routing, institutional graded-source retention (P5.10) and generic staff-reference recovery remain existing backend/infrastructure follow-ups.
- Backend Java tests were not rerun for this frontend-only step. The accepted Step 6 backend evidence remains unchanged. Research datasets/models/metrics, TeX/PDF packages and classroom-effectiveness claims were not changed.

The user authorized the scoped semantic commit and push. Unrelated untracked datasets, paper materials and local files are excluded.
