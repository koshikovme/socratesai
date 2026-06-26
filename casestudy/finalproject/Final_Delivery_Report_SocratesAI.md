# Final Delivery Report - SocratesAI

## 1. Product Summary

SocratesAI is a real-time programming mentor for introductory Java students. It addresses the problem that first-year students often get stuck on small syntax and logic mistakes during labs or practice and do not receive timely individual help. Its primary user is a first-year computer science student who needs short, bounded guidance inside the coding workflow rather than a full answer from a general chatbot.

## 2. Requirements Delivery

| ID | Requirement | Type | Final Status | Evidence / Note | Sprint |
|---|---|---|---|---|---|
| FR-01 | Monitor code changes in real time | Functional | Implemented | `TaskSolveView.vue` watches code changes, debounces updates, and sends WebSocket messages; `CodeRealtimeController.java` handles `/app/code.update` and returns feedback to `/user/queue/feedback`. | Sprint 3 |
| FR-02 | Detect syntax, beginner, and simple logic errors | Functional | Partial | `AnalyzerService`, `SyntaxCheckEngine`, and `PatternDetectionEngine` detect syntax errors, empty editor state, off-by-one loops, and suspicious `while(true)` logic, but the analyzer is still heuristic and narrow. | Sprint 2 |
| FR-03 | Maintain a simple student model | Functional | Implemented | `InteractionLogService.buildStudentContext()` tracks repeated errors, last feedback action, last feedback success, and total session feedback count. | Sprint 2 |
| FR-04 | Generate adaptive feedback | Functional | Implemented | `MentorPolicyService` selects one of four actions and `FeedbackGenerationService` returns either a template response or Gemini phrasing. | Sprint 4 |
| FR-05 | Avoid giving full solutions | Functional | Partial | The bounded action set and the Gemini prompt explicitly forbid full solutions and corrected code, but there is no hard post-generation checker or strict graded-task mode yet. | Sprint 4 |
| FR-06 | Respond almost instantly during coding | Functional | Partial | In pilot logs collected for the MVP evaluation, the mentor loop recorded mean latency of `740 ms` and P95 latency of `800 ms`, but performance still depends on optional ML/LLM services and was not load-tested in a real class. | Sprint 5-6 |
| FR-07 | Log all interactions for future improvement | Functional | Implemented | `InteractionLogService.saveInteraction()` stores each mentor interaction and `/api/interactions/policy-dataset` exports a CSV dataset for later policy training. | Sprint 1-5 |
| NFR-01 | Performance: feedback should appear during coding, ideally under 1 second | Non-functional | Partial | The prototype met the latency target in pilot logs, but I did not complete multi-user performance testing. | Sprint 5-6 |
| NFR-02 | Usability: integrated into a web-based IDE with simple feedback | Non-functional | Implemented | The frontend provides login, dashboard, Monaco-based solve view, realtime toggle, and a compact feedback panel in one flow. | Sprint 3-4 |
| NFR-03 | Reliability: stable during coding sessions | Non-functional | Partial | The backend passes `57` tests and the frontend passes `10` tests, with validation and fallback behavior in place, but I have not yet validated long-running session stability in production conditions. | Sprint 6 |
| NFR-04 | Scalability: support many students / larger classes | Non-functional | Partial | The architecture is modular and `StudentTaskSessionServiceConcurrencyTest` confirms one active session per student-task pair under concurrent requests, but I did not run a full classroom-scale load test. | Sprint 5-6 |
| NFR-05 | Security and privacy of interaction data | Non-functional | Partial | JWT protects authenticated APIs, passwords are hashed, WebSocket connections use token-based auth, and interaction logs store a code hash instead of raw code snapshots. However, dataset export still contains student and task identifiers, and account erasure is not complete. | Sprint 2 and 6 |
| NFR-06 | Maintainability through modular architecture | Non-functional | Implemented | The backend is split into analyzer, mentor, policy, interaction, session, auth, task, and user modules. `mvn verify` passes with core coverage checks, and the current core backend coverage report is `81.3%` line and `59.6%` branch. | Sprint 1-2 |
| NFR-07 | Ethical design: support learning, not cheating | Non-functional | Partial | The mentor is constrained to four pedagogical actions and short feedback, but stricter classroom controls and stronger anti-answer enforcement are still future work. | Sprint 4-5 |

In total, `7` requirements were fully implemented, `7` were partial, and `0` were descoped. The partial items do not break the core mentor loop, but they matter: the delivered product works as a real MVP, not yet as a classroom-ready platform with proven scale, full privacy governance, or strong formal guarantees against answer leakage.

## 3. MVP Hypothesis Outcome

**Original hypothesis from Assignment 4 (verbatim):**  
**If I build a real-time mentor that analyzes beginner Java code and responds with one of four actions - code highlight, conceptual hint, guiding question, or no feedback - I believe first-year CS students will use it during problem solving because bounded help inside the coding workflow is more useful than either silence or an unrestricted AI answer.**

| Question | My answer |
|---|---|
| What evidence supports it? | The strongest evidence is that the full loop works in real use rather than only in design. A student can open a task, write code, request mentor feedback manually or through realtime updates, and then mark whether the feedback helped. In the pilot interaction logs I recorded `570` event-level interactions, with mean end-to-end latency of `740 ms`, P95 latency of `800 ms`, and an aggregate event-level resolution rate of `70.0%`. That is not proof of product-market fit, but it is real evidence that bounded feedback can stay inside the solving flow and sometimes help students recover quickly. |
| What did not validate? | The retention part did not validate yet. I still do not know whether first-year students will return and use SocratesAI again on a second task within the same week without being forced by an instructor. I also learned that the analyzer is much narrower than I first assumed: it can support a focused CS1 debugging workflow, but it is not a general-purpose code tutor. |
| What changed as a result? | I became stricter about scope. I kept the mentor centered on four bounded actions and treated logging, outcome capture, and policy control as first-class features, while leaving out broader ideas such as open-ended AI chat, code execution, teacher analytics, and multi-language support. I also kept the ML policy service optional, with rule-based fallback, because the early dataset is not yet strong enough to justify an ML-only decision path. |

## 4. Development Process

| Item | What I did |
|---|---|
| Methodology | I used **Scrum**, as selected in Assignment 2. In practice, I applied it as a lightweight solo process with six planned sprints: requirements and architecture first, backend mentoring pipeline next, then frontend integration, feedback refinement, ML extension, and finally testing plus documentation. |
| GitHub evidence | The commit history shows work distributed across multiple dates rather than a single final push: `2026-04-03`, `2026-04-04`, `2026-04-09`, `2026-04-10`, and `2026-05-05`. Main history: `https://github.com/koshikovme/socratesai/commits/main`. Example commits with visible diffs: initial full scaffold and core implementation `3a164a8` (`https://github.com/koshikovme/socratesai/commit/3a164a8c354761350a6e1a314cc5f5d9233491ed`), test and integration expansion `90958c3` (`https://github.com/koshikovme/socratesai/commit/90958c3207bba6d6118ba6a93762e8810f43a72a`), validation and E2E quality gates `9a5cfb1` (`https://github.com/koshikovme/socratesai/commit/9a5cfb17d4aabeb1fd52a1a71ae22fa80dd1b998`), and analyzer/feedback test growth `20cf6d3` (`https://github.com/koshikovme/socratesai/commit/20cf6d3770c0b02d441c8d03286e64adee038bd1`). |
| Ceremonies / artefacts | Because this was a solo academic project, I used the sprint plan and requirement traceability table as the main artefacts. Sprint planning and review happened in practice, because I worked against the six-sprint plan and checked progress against requirements, but I did not run formal retrospectives after every sprint. The process was structured, but lighter than a team Scrum setup. |
| What I learned | Scrum helped most when it forced me to separate the core mentoring loop from attractive side features. If I did this again, I would define "done" more strictly for non-functional work earlier, especially privacy cleanup, deletion flow, and load/performance validation, instead of treating those as late-stage polish. |

## 5. Theory in Practice

| Item | My answer |
|---|---|
| Concept | **Innovation accounting** from Eric Ries, *The Lean Startup*, p. 18 and Chapter 7. Ries argues that startup progress should be measured with learning-oriented metrics, not vanity activity counts. |
| Where it appeared | This showed up when I designed the interaction logging and feedback review flow. Instead of measuring success by raw mentor request count or time spent in the editor, I added fields such as `resolved_after_feedback`, `fixed_after_ms`, `same_error_count`, `last_feedback_action`, and `total_feedback_count_in_session` to `InteractionLog`, and I added the "It helped / Still stuck" outcome step in the UI. |
| Outcome | Applying this idea helped because it gave me a more honest way to evaluate whether the mentor was actually useful. It also exposed a real limitation in the project: I can measure short-term recovery inside one task, but I still do not have strong evidence for repeat usage across different tasks, which is why I do not claim product-market fit yet. |

## 6. Architecture Delta

| Component / Decision | Original Design | Final Implementation |
|---|---|---|
| LLM feedback provider | The earlier design treated the phrasing layer as a generic LLM provider slot (`Gemini / OpenAI`). | In the delivered system, **Gemini** is the only provider wired into the active `FeedbackGenerationService`. OpenAI support exists in the codebase as an experimental integration, but it is not part of the main runtime feedback path. I kept one active provider to reduce integration complexity and preserve a clear template fallback path. |

Other than that, the final container-level architecture stayed close to Assignment 3: Vue frontend, Spring Boot backend, PostgreSQL database, and an optional Python ML policy service with rule-based fallback.
