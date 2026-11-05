# Final implementation and production handoff

Date: **2026-11-05**. Live installation: **https://socratesai.duckdns.org**.
Current release **v0.9.3** (`c53bf74f3b9e6a7be622f4e2a475dc9ff52fa9eb`) passed
[automatic release/deployment](https://github.com/koshikovme/socratesai/actions/runs/41961137181).
[Permanent digest/model/runtime manifest](../../deploy/releases/v0.9.3.json).
The original v0.9.1 installation/recovery evidence remains dated in the production report;
[the follow-up](result-mentor-v2.md) records current AI/workspace and patched-image acceptance.
Operational helper/documentation commits follow the tagged application source; the tag is unchanged.

| Requirement | Implementation / evidence |
| --- | --- |
| Native DTO/schema/versioning | Problem/collection/submission/execution entities, repositories and additive Flyway migrations; [schema report](result-1-schema.md). Production has 13 successful migrations, including V13 quota counters. |
| Isolated compilation/execution | CodeExecutionPort, bounded Judge0 adapter, deterministic comparator and durable case mapping; [adapter report](result-3-sandbox.md), [certified executor packaging/catalog](../../deploy/sandbox/README.md). Dedicated VM, all three actual compilers/runtimes, resource/privacy boundaries verified. |
| Staff security/authoring | STUDENT-only public signup, audited role provisioning, ownership, immutable publication after reference verification, collection locking; [authoring report](result-4-authoring.md). New production version verified/published in all three languages. |
| Real grading/progress | Idempotent 202 submission/run APIs, atomic attempts, SKIP LOCKED jobs, fenced leases, persisted provider tickets, complete-suite acceptance and accepted-derived progress; [submission report](result-5-submissions.md). Real AC/WA/CE/RE/TLE/output cases and crash recovery passed. |
| Mentor/ML compatibility | Principal-bound STOMP, correlated native contexts, Python model gating, Gemini hints/classification for multilingual or unavailable-model contexts, current-hash execution evidence and additive export provenance; [current follow-up](result-mentor-v2.md), [original mentor report](result-6-mentor.md). Actual code-only ML/Gemini requests and WSS passed; model hashes unchanged. Native students never receive RULE/template fallback. |
| Student/teacher journeys | Native catalog, isolated language buffers, async judging, hidden-safe results, staff editors, SafeMarkdown and logout cleanup; [frontend report](result-7-frontend.md). Fresh real HTTPS/WSS/Monaco browser journey passed after deployment. |
| Reproducible delivery | Non-root layered images, same-origin TLS edge, private Compose, startup validation, pinned dependencies/actions, Testcontainers/Flyway and frontend/model/build/security gates; [delivery report](result-8-delivery.md), [current runbook](../../DEPLOYMENT.md). Four release image artifacts published; exact three app digests automatically deployed. |
| Production operations | Authorized separate hosts, encrypted private execution transport, nonsuperuser DB roles, TLS renewal, durable volumes/limits, encrypted backups/off-host copy, actual restore, rollback/outage/lease rehearsals; [production report](result-9-production.md). |
| Bounded LLM spending | Gemini classification/phrasing uses durable month/minute/student-day quotas, counted input and output caps, explicit provider/allowance errors. Reviewed configured upper estimate $2.8032/month; tax, price changes and outside-key use excluded. No paid request for untouched onboarding or already accepted graded work. |
| Research integrity | Saved models, experiment result folders, TeX and figure artifacts were not changed. Public Codeforces evaluation, weak labels, genuine interaction data and synthetic production probes remain distinct. No new classroom effectiveness claim. |

Current verification includes 319 Java tests (0 failures/errors, one opt-in experiment skipped),
93 frontend tests/lint/build, Python inference/readiness contracts, two CI browser fixtures,
four real build-context exclusion checks, six delivery tests, five demo fixture tests and
successful image/security/release gates. The actual live probes are separate evidence:
three-language fresh Run/Submit/idempotency, twelve verdict cases, actual browser, hidden echo,
foreign access/principal spoofing, anonymous/foreign WSS denial, real Gemini hints before any
execution, starter reset and 30 real concurrent AI hints followed by 30 sample runs. Backend
recovery, executor/model outage, compatible rollback and encrypted restore retain their earlier
v0.9.1 evidence; the historical model-outage RULE behavior was replaced by native Gemini in
v0.9.3 and its failure paths have regression coverage.

Security review rejected the legacy Debian 10 executor. Its project-maintained replacement
now includes the APT 2.6.2 security fix and has zero recorded fixed HIGH/CRITICAL findings,
as do the freshly gated application images and previously checked
Redis image. Stock PostgreSQL's unused gosu Go-helper findings are separately reported and
reviewed under explicit UID-70/read-only/capability restrictions. Unknown, lower-severity,
unfixed and future findings are not covered by that dated gate. Secrets/access/backup keys,
provider tickets, private source and unrelated research files were excluded from commits.

The IEEE integration section was inspected: endpoint, Python features, threshold 0.35 and
confidence logging remain compatible. No paper edits, new research runs, duplicate-source
synchronization or PDF rebuild is claimed. The production database is fresh and synthetic;
no migration/testing was performed on shared real developer/student databases 21/22.

The owner still needs to choose long-term graded-source retention/deletion and external
incident notification routing, keep recovery material independently safe, and prepare the
actual teaching catalog/accounts. The current small-problem 30-session result is a bounded
starting point; monitor queue pressure for compiler-heavy classes. No additional VPS, domain,
Vercel or managed Judge0 subscription is needed for the recorded deployment.
Owner credentials remain in protected local `.delivery/production-access.md`.
