# SocratesAI native frontend

Vue 3, Composition API, Pinia and Vite. This application uses the native versioned problem/collection APIs, asynchronous sample runs and graded submissions, and authenticated STOMP mentor feedback. It does not fetch an external task catalog.

## Local development

Use Node 22 (22.23.1 verified), with the committed lockfile:

```sh
npm ci
npm run dev
```

Vite serves port 5173 and proxies `/api` and `/ws` to `http://localhost:8080`. Start the backend separately with an isolated development database. Neither frontend tests nor browser fixtures need a shared database or execute student code.

Optional build-time variables: `VITE_API_BASE_URL` and `VITE_WS_BASE_URL`. Empty values use the same origin, recommended behind the production reverse proxy. Never put server API keys, database credentials or JWT signing secrets into Vite variables. Browser Web Crypto requires HTTPS or localhost.

## User journeys

- `/dashboard`: bounded native catalog pages; title/topic, difficulty and solved filters explicitly apply to the current page. Backend catalog APIs currently support `page,size` only. SOLVED comes from judged progress, not mentor feedback.
- `/collections/:id`: ordered, pinned published versions and exact-version accepted progress.
- `/problems/:id?version=…&collection=…`: statement/samples, independent in-memory language buffers, Monaco, mentor, Run samples, Submit and paged owner history. Language switching preserves edits. Reload/logout discards code; navigation warns about unsaved buffers. Up to 24 context buffers are kept; discard other buffers explicitly when needed.
- `/teacher`: staff-owned problems and collections. Draft editing, language limits, public/hidden tests, reference verification, immutable publication, explicit pinned-version updates, atomic order saves and conflict reload are supported.
- `/admin/users`: authenticated administrator role changes by user ID, expected role, new role and audit reason. First-admin creation is external backend configuration; public signup only creates students.

All roles are rechecked through `/api/users/me` before protected navigation. Backend authorization remains authoritative. HTTP 401 clears credentials, stores, private buffers, sockets and timers, then redirects to login. Other API failures map trusted envelope codes to fixed messages; raw response bodies are never displayed.

## Mentor and judging contracts

STOMP CONNECT carries `Authorization: Bearer …`; subscribe only to `/user/queue/feedback`, send only `/app/code.update`. Full snapshots contain `problemId`, `problemVersionId`, `languageKey`, `editorSessionId`, `codeRevision`, `code`. No client identity, attempt count or analyzer evidence is sent. Replies must match all context fields plus SHA-256 of the current code. Typing is debounced; reconnect and HTTP recovery advance the revision, with `/api/mentor/native-feedback` as the fallback. Mode and unknown execution evidence are visible. Python alone is eligible for the current ML model; other configured languages use RULE.

Run and Submit use separate native APIs and fresh UUID idempotency keys. A lost create response retains the exact source/key for explicit safe retry. Once an ID exists, recovery uses GET only. Polling uses capped backoff and a six-minute monitoring window. `SYSTEM_ERROR` with `retryScheduled=true` remains pending. Paused monitoring can be resumed; graded history survives page reloads. Only the backend allocates attempt numbers and grants accepted progress.

Current run/submission DTOs expose metric-only cases. The UI renders an explicit allowlist of verdict, time and memory, never arbitrary stdout/stderr/diagnostics. Even injected hidden-output fields are ignored. Public sample input/expected output comes only from the published problem projection. `SafeMarkdown` intentionally displays escaped text with whitespace preserved; HTML is not interpreted.

## Validation

```sh
npm test
npm run test:coverage
npm run build
npm run test:e2e
```

The browser suite uses the production build and Chrome (`PLAYWRIGHT_CHANNEL=chrome` default). Install Chrome or set an installed compatible channel; Playwright browsers can also be installed separately. Browser tests exercise synthetic HTTP and real browser SockJS/STOMP protocol fixtures, including authoring, pinned collections, all three editor languages, mentor correlation, runs, submissions, progress, hidden-data masking and denied access. These fixtures do not certify a real Judge0 host or production deployment.

Monaco loads only with the solver route and bundles its same-origin worker and Java/Python/C++ language definitions. Production hosting must serve `index.html` for SPA deep links, proxy `/api` and SockJS `/ws/**` (including upgrades), enforce HTTPS and avoid caching authenticated API responses. This repository step validates Vite preview fallback; the production edge configuration belongs to the infrastructure step.

Dependencies and advisory fixes are locked. DOMPurify is overridden to 3.4.17 because the pinned Monaco version otherwise installs a vulnerable older version; the application itself never uses raw HTML rendering. See `../tasks/socrates-evolution/result-7-frontend.md` for exact test/build evidence and current limits.
