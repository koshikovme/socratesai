# Typed starters, staff results and beginner collections

Date: **2026-11-05**. Implementation/local verification is complete; production release,
course publication and live staff/student acceptance are pending in this initial source report.

## Starter contract

Teachers can generate a full program around named typed parameters and a return type, for
example `static int add(int firstNumber, int secondNumber)` in Java. `main` reads values and
prints the returned answer. The generated function has a TODO placeholder, not a solution.
The builder supports Java, C++17 and Python; scalar integers, long integers, one-word text,
and length-prefixed integer arrays. Other input formats remain author-editable full source.
Names/types/counts are validated; reserved/adapter names and duplicates are rejected.

Generated code uses the existing versioned `starterCode` field. No schema migration,
function-only execution mode, local compiler or guessed task-specific parsing was introduced.
Publication still requires sandbox verification. Existing authored code is replaced only
after an explicit teacher action and confirmation. Workspace help now follows the supplied
function signature instead of hardcoding `solve(Scanner input)`.

## Staff monitoring

- Task and collection editors/list pages link to **Student results**, available to the owner
  and administrator. Lists have paging, student/email/group search, exact verdict/status
  filters, per-student drill-down and optional visible-page auto-refresh every 10 seconds.
- New GET routes: `/api/teacher/problems/{id}/submissions` and
  `/api/teacher/collections/{id}/submissions`. Bounded list/aggregate queries omit source.
- Existing `/api/teacher/submissions/{id}` remains compatible with `submission`/`sourceCode`
  and adds student identity, task title and diagnostic-free case metrics on explicit review.
  Source renders as escaped text and is cleared/aborted on navigation/logout.
- A task owner can review attempts on that task. A collection owner can review only attempts
  explicitly submitted with that collection context, including archived/historical attempts.
  Copying a public task into a collection never exposes its unrelated student attempts.
  Administrator access remains global; student/anonymous/foreign staff access is denied.
- Stats count formal attempts, distinct submitters, accepted submissions and pending work.
  Samples/typing never become attempts; no manual grade, score/rubric or new evaluation field
  was added. Judge verdicts remain the grading result and sole source of solved progress.

## Beginner material and checks

`content/basics/problems.json` contains ten public exercise definitions: sums, rectangle
perimeter, maximum, parity, summation, factorial, digit counting, arrays and vowels. Each
Java/C++ collection has ten separate language-specific native problems, explicit typed
starters, public samples and a private author validation suite. Statements are Russian.
`scripts/curriculum/build_starters.mjs` creates scaffolds without executing programs;
`publish_basics.py` imports through normal staff APIs with per-phase recovery state.
Uncertain create/verification requests stop for reconciliation; completed imports replay without duplicate publication.
Real hidden inputs and reference programs stay in protected `.delivery/` files outside Git
and frontend assets. Nothing enters mentor prompts or student DTOs.

Local backend `mvnw verify`: **322 tests**, zero failures/errors, one opt-in skip, coverage
gates passed. **130 frontend tests**/coverage, lint/build and three Chromium protocol-fixture journeys
cover named Java generation, real API values under translation, staff review, safe source/
hidden output and mobile layout. Nine delivery tests include publication validation/replay.
All **20 generated Java/C++ starters compiled and launched on the dedicated executor** with
public sample input. No student/reference program was executed locally or on the app host/CI.
Production outcomes and exact counts/IDs will be appended after release and publication.

Research models, datasets, interaction schema and papers are unchanged. This prepared
curriculum and synthetic acceptance do not establish classroom learning gains.
