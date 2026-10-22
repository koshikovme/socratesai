# Step 6 — WS/ML и доверенный native-контекст

**Дата:** 2026-10-22. **Базовый HEAD:** `2f4d094`. Запрошенная backend-логика реализована и проверена локально. Текущий запрос разрешает scoped commit и push.

## 1. WS-протокол и identity

`/ws` сохраняет SockJS/STOMP transport. CONNECT требует единственный `Authorization: Bearer <JWT>` в **STOMP headers**, не query string. Подпись/expiry проверяет существующий `JwtService`; account ID и актуальные authorities берутся из БД. Предустановленный клиентом Principal не используется. Сервер связывает transport session ID с immutable identity и сроком действия JWT; bearer и password hash в socket principal не сохраняются.

SEND/SUBSCRIBE проверяют привязку сессии, expiry и актуальный аккаунт. Новый Authorization на SEND не меняет identity. Авторизационные headers удаляются до дальнейшей обработки/отказа. Разрешены только:

- SEND `/app/code.update`;
- SUBSCRIBE `/user/queue/feedback`, одна активная subscription на transport session;
- служебные heartbeat, UNSUBSCRIBE и DISCONNECT в рамках своей сессии.

Прямые broker destinations, чужие `/user/{name}/...`, `/queue/...`, `/topic/...` и другие application routes запрещены. Отказ возвращает фиксированный ERROR frame и не передаёт сообщение обработчику. Ошибки не отражают failed message, JWT, source или provider response. Безопасный явный ERROR также обходит особенность Spring ordered inbound channel, который может поглощать исключения interceptor и логировать failed frame.

Ответ направляется через user destination с **server transport session ID**, поэтому другая вкладка того же аккаунта не получает этот ответ. Истекший/отключённый socket не получает поздний результат. Удаление аккаунта/утрата доступа повторно проверяется перед записью feedback.

Официальные механизмы: [Spring STOMP token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html), [Spring user destinations](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html). Их фактическая интеграция проверена через TCP WebSocket/SockJS/STOMP в тестах.

Новый body — полный snapshot, не diff:

```json
{
  "problemId": 123,
  "problemVersionId": 456,
  "languageKey": "python3",
  "editorSessionId": "67c05537-ef86-4557-b35f-1dd67645b212",
  "codeRevision": 7,
  "code": "a, b = map(int, input().split())\nprint(a + b)"
}
```

`studentId`, старые `taskId`/`attemptNo`, клиентские analyzer results/context игнорируются и не являются полями native DTO. Problem/version должны быть валидными опубликованными native snapshots, язык — настроенным для версии. Source ограничен `ContesterLimits`: 20 000 UTF-16 characters, 80 000 UTF-8 bytes, корректный Unicode; пустой editor buffer допустим. Revision — целое 0…9 007 199 254 740 991.

`editorSessionId` — отдельный UUID редактора; при смене problem/version/language требуется новый UUID. Revisions строго возрастают; дубликаты и старые снимки не запускают повторный inference. Reconnect может продолжить тот же editor/context с возрастающей revision. Серверный latest state имеет ключ `(authenticated student, editor UUID)`; другая вкладка с собственным UUID не вытесняет её.

Feedback содержит editor/context/revision, SHA-256, interaction/session IDs, evidence origin, `executionKnown`, nullable compilation/tests, public verdict, policy mode/version/fallback reason и model state/confidence. Клиент должен проверять context/revision/hash перед применением ответа. Обработка ошибок использует safe envelope `{code,message,requestId}`; superseded HTTP request получает 409.

## 2. Native HTTP fallback и legacy compatibility

`POST /api/mentor/native-feedback` принимает тот же snapshot и JWT. Использует общие rate limits, latest-state coordinator, validation и серверную identity; результат приходит асинхронным HTTP response.

Старый body с внешним task ID не преобразуется в native version по численному совпадению. Старые `/api/mentor/feedback`, `/api/mentor/analyze-feedback` и `/api/analyzer/**` сохранены как **administrator-only research routes**. Обычный студент не может передать произвольный analyzer result/attempt/identity через них. Формат controlled research requests сохранён; явная `NO_POLICY` ablation сохраняет исходную семантику. Opt-in replay test provision-ит оператора только в изолированном test profile; production registration по-прежнему создаёт только STUDENT.

Старый frontend, отправляющий LeetCode/taskId body, требует адаптации в Step 7. Нового публичного fallback на внешние numeric IDs нет.

## 3. Evidence и транзакции

`NativeMentorStore.prepare` в короткой транзакции проверяет аккаунт, published version и language; создаёт отдельную native editor session. Существующие legacy sessions не переинтерпретируются. Formal attempt number берётся из реального student/problem progress; при отсутствии formal submissions это **0**. Typing, inference, sample feedback и outcome update не увеличивают formal attempts и не меняют solved progress.

`MentorEvidenceResolver` выбирает последний FINISHED SUBMISSION либо неистёкший SAMPLE_RUN, совпадающий по:

```text
student + problem + problemVersion + language + SHA-256(full current buffer)
```

Case evidence читается отдельной projection с status/verdict. Она не загружает stdout/stderr/compile output, provider tokens, test input/expected output, reference solution. Пустая/незавершённая case evidence не считается выполненной проверкой. Инфраструктурная SYSTEM_ERROR не становится compilation error или failed test по догадке.

| Доказательство | Native DTO / interaction_logs | Legacy PolicyFeatures |
| --- | --- | --- |
| Нет подходящего завершённого execution | `executionKnown=false`, origin `STATIC_HEURISTIC`, compile/tests/verdict **null**, error type `EXECUTION_UNKNOWN` | Прежние primitive boolean/int поля сохраняют false/0 compatibility sentinels; они не являются результатом запуска |
| Public Sample Run | origin `SAMPLE_RUN`, реальные compilation/pass/fail counts и verdict | Маппинг реальных значений; sample success отдельно маркируется `SAMPLE_PASSED` |
| Formal submission | origin `JUDGED_SUBMISSION`, реальные counts/verdict и submission ID | Маппинг нормализованных verdicts в policy error types |

COMPILATION_ERROR → SYNTAX_ERROR; WRONG_ANSWER → WRONG_CONDITION; runtime/time/memory/output failures сохраняют соответствующий failure type. ACCEPTED sample не означает решение полной задачи. Только принятый formal submission из Step 5 определяет SOLVED.

Inference/LLM работают **вне DB-транзакции**, в отдельном bounded executor. Перед сохранением снова проверяется аккаунт/expiry/access. Запись feedback и увеличение native feedback count атомарны под session lock. Coordinator проверяет revision/connection до короткой транзакции сохранения и перед отправкой, отбрасывает superseded/timeout/disconnected work. Прогресс и graded counter этот workflow не записывает.

## 4. ML, RULE и optional LLM

Codeforces-модель имеет ровно одну eligible canonical language key: **`python3`**. Java, `cpp17`, другие/неизвестные aliases используют RULE и deterministic templates; source не отправляется Python ML и optional LLM. Java-oriented legacy analyzer не запускается для native Python/C++ feedback; student code нигде не исполняется внутри backend.

Для Python `/predict-mentor-state` получает явные `language_key=python3`, `language_variant=Python 3` и прежние feature keys. `PolicyFeatures` record и naming не менялись. При неизвестном execution compile/tests/passed-test-count уходят **null**, а не как наблюдаемые нули. Source-only inference остаётся доступным; prediction `accepted` не является grader verdict и не решает задачу.

FastAPI требует explicit language key, отклоняет Java/C++/отсутствующий язык и несовместимый language_variant. Ошибки валидации не содержат исходный запрос. Сбой модели даёт fixed 503. `execution_known=false` обнуляет optional run metrics; time/memory поля остаются optional. Текст для модели ограничен 5 000 characters; numeric source metrics считаются по полному bounded buffer. Счётчики LF/blank/comment lines согласованы с `train_codeforces_feedback_need_model.add_code_metrics`, включая trailing LF.

- `APP_POLICY_ML_MIN_CONFIDENCE` действительно привязан в обычном и prod YAML; default 0.35.
- Low confidence сохраняет predicted mentor state/confidence в логах, но action выбирается RULE.
- Disabled/failed/invalid native ML inference всегда даёт RULE/template с фиксированным fallback reason, включая старую настройку `fallback-to-rule=false`.
- ML response ограничен 64 KiB до JSON parsing; source/body не попадают в исключения, policy/request `toString()` замаскирован. HTTP connect/request bounds — 2s/5s.
- Default saved artifact `ml/codeforces_feedback_need/source_code_model.joblib`, endpoint и configured model version сохранены; `MENTOR_STATE_MODEL_PATH` по-прежнему выбирает другой saved pipeline. Новых model weights или результатов обучения нет.

Optional LLM phrasing применяется в native ML-пути при известной execution evidence; RULE/unknown execution используют deterministic templates. При сбое Gemini/OpenAI текст падает в template; policy mode описывает выбор action и может оставаться ML, когда упало только phrasing. Это две разные fallback-границы.

`MentorPrompt` сериализует только action, фиксированный error type, execution-known flag, student source ≤5 000 characters и public statement ≤2 000 characters. Entity/provider response dumps, hidden/sample test bodies, reference code, credentials, diagnostics и arbitrary analyzer signals не включаются. Student source/public statement явно описаны как untrusted data; trusted instruction отделена от JSON данных. У LLM нет tools/backend-secret context. Ответ ограничен 1 200 characters; code fences/пустой ответ дают template fallback. Это ограничение контекста и формата, не доказательство полной устойчивости LLM к prompt injection.

Gemini key перенесён из URL query в `x-goog-api-key`; trusted system instruction передаётся отдельно. Формат подтверждён [официальной Gemini API документацией](https://ai.google.dev/api/generate-content). Provider connect/read bounds — 2s/3s. Реальные платные LLM calls не выполнялись.

## 5. Debounce, capacity и transport limits

| Настройка | Default |
| --- | --- |
| `APP_MENTOR_DEBOUNCE_MS` | 250ms |
| `APP_MENTOR_MIN_INTERVAL_MS` | 1 000ms между inference starts одного editor |
| `APP_MENTOR_TIMEOUT_MS` | 15 000ms на работу, с cancellation/stale guard |
| `APP_MENTOR_CONCURRENCY` | 4 inference workers; очередь 64 |
| `APP_MENTOR_MESSAGES_PER_SECOND` | 20 account-wide SEND/SUBSCRIBE/HTTP events в фиксированном секундном окне |
| `APP_MENTOR_SESSIONS_PER_USER` | 4 WS sessions |
| `APP_MENTOR_EDITORS_PER_USER` | 4 editor states |
| `APP_MENTOR_MAX_SESSIONS`, `APP_MENTOR_MAX_EDITORS` | по 1 024 на process |

Настройки валидируются. На editor удерживается последний snapshot и не больше одной активной работы; pending timers заменяются, старые futures отменяются. Idle editor state удаляется через 5 минут, периодический проход — раз в минуту; disconnect удаляет связанные состояния. Token expiry проверяется при frames и completion; idle socket не получает новый inference data после expiry.

STOMP inbound/outbound executors: по 4 потока, очередь 128. Message limit 128 KiB, send buffer 256 KiB, send time 5s, first-message time 10s. Tomcat outer text buffer 256 KiB учитывает SockJS JSON escaping; внутренний STOMP limit остаётся 128 KiB. Реальные 20 000-character buffers проверены через transport, а не только DTO validator.

Rate/editor/session limits и debounce process-local. При нескольких backend instances нужны sticky WS routing и согласованное внешнее admission, прежде чем обещать общий capacity budget. Текущие лимиты не означают подтверждённый production throughput.

## 6. Исследовательская совместимость и миграция

[V12__native_mentor_context.sql](../../src/main/resources/db/migration/V12__native_mentor_context.sql) добавляет editor session ID и optimistic session version; отдельные active indexes позволяют независимые editor/version contexts, сохраняя совместимость старых nullable editor rows. `interaction_logs` дополняется editor ID, execution job ID, execution-known и fixed policy fallback reason. Все старые поля, V1–V11 и сохранённые строки остаются на месте.

`execution_job_id` использует `ON DELETE SET NULL`: TTL-cleanup Sample Run не удаляет исследовательские записи и не блокируется их FK. Исторические origin/hash/compile/test snapshots сохраняются; очищается только ссылка на удалённую job. Это проверено PostgreSQL integration test.

`GET /api/interactions/policy-dataset` и `resolvedOnly`, filenames/content type/старые колонки и их порядок сохранены. В конце добавлены `problem_id, problem_version_id, language_key, context_source, evidence_origin, code_revision, editor_session_id, execution_job_id, execution_known, submission_id, policy_fallback_reason`. SQL-export также дополнен metadata без изменения старых compatibility projections.

Глобальные dataset/label exports и agreement требуют ADMIN. Собственный outcome может менять владелец interaction (либо admin); другой студент получает отказ. Teacher label разрешён только для собственного native problem и reviewer ID текущего преподавателя. Административный research scope сохраняет работу со старыми reviewer IDs. Старые expert-label CSV columns сохранены, provenance добавлена в конец.

Осмотрены IEEE integration section и saved result JSON: предмет модели остаётся Python Codeforces, endpoint `/predict-mentor-state`, default confidence threshold 0.35 и experimental artifacts не изменились. Новые runtime guards/provenance описаны здесь и в `ml/README.md`; TeX/PDF и дубликаты не редактировались/не пересобирались. Метрики Codeforces/policy-action/HTTP experiments не смешивались. Старые benchmark результаты остаются историческими; opt-in harness обновлён для administrator-only research access, но эксперименты не запускались и outputs не перезаписывались.

## 7. Проверки

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
bash mvnw -B clean verify

PYTHONDONTWRITEBYTECODE=1 /tmp/socrates-mentor-venv/bin/python ml/test_policy_api.py
PYTHONDONTWRITEBYTECODE=1 /tmp/socrates-mentor-venv/bin/python ml/test_policy_model.py
```

**Java:** BUILD SUCCESS; **282 tests, 0 failures/errors, 1 existing opt-in replay skipped**. Полный clean verify — 1:02. JaCoCo global: lines **90.89%**, branches **69.60%**; core: lines **92.02%**, branches **70.04%**; все существующие gates пройдены.

**Новые Java проверки:** 16 real TCP STOMP/SockJS + disposable PostgreSQL cases; 5 coordinator cases; 11 native-policy cases; 3 socket-security cases; 2 prompt tests. Покрыты spoofed ID/principal, anonymous/expired token, foreign/broker subscriptions, session-specific delivery/cross-tab isolation, current-hash/owner evidence, rapid typing/stale in-flight completion, deadlines/cancellation/quotas, oversized buffers, Java/C++ no-network gating, actual ML HTTP outage, LLM/template fallback, safe exports/outcomes/labels, HTTP fallback, V12 schema и sample cleanup provenance. Existing sandbox/authoring/submission suites проходят вместе с ними.

**Python:** 7 passing contract/saved-model smoke tests. Default repository action/source models загружены и выполнили inference через FastAPI TestClient без обучения. Отдельный `ml/test_policy_model.py` также завершился успешно. Временный venv использует Python 3.12 и pinned serving packages: FastAPI 0.116.1, uvicorn 0.35.0, pandas 2.3.2, scikit-learn 1.7.1, joblib 1.5.2, test httpx 0.28.1. Есть неблокирующий joblib/NumPy deprecation warning; новые evaluation metrics не вычислялись.

Артефакты: `target/surefire-reports/`, `target/site/jacoco/`, `target/site/jacoco-core/`; локальные логи `/tmp/socrates-step6-verify.log`, `/tmp/socrates-step6-python-tests.log`, `/tmp/socrates-step6-policy-smoke.log`. Python test dependencies записаны в `ml/requirements-test.txt`. В CI Python serving checks требуют соответствующих зависимостей; интеграция общего CI остаётся Step 8.

Все PostgreSQL datasource получены от изолированных Testcontainers, `.env` import в таких тестах отключён. Shared БД 21/22 и production не использовались. Студенческий код выполняется только через sandbox port; тестовые port results — synthetic fixtures. Реального Judge0 по-прежнему нет; изоляция sandbox, реальные provider credentials/availability, production hosting и нагрузочная ёмкость этим шагом не проверены. Следующий этап — frontend/native workspace и дальнейшая инфраструктура согласно плану.
