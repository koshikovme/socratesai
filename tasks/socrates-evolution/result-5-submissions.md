# Step 5 — отправка решений, durable judging и реальный прогресс

**Дата:** 2026-10-22. **Базовый HEAD:** `25a95ae`. Запрошенная backend-логика реализована локально; публикация scoped commit/push разрешена текущим запросом пользователя.

Отдельного Judge0-сервера пока нет. Проверки orchestration используют настоящий disposable PostgreSQL и управляемый `CodeExecutionPort`; существующие HTTP-протокольные тесты адаптера используют локальный сервер. Студенческие программы на машине приложения не компилируются и не запускаются. Реальное исполнение, изоляция и production capacity этим результатом не подтверждаются; `APP_JUDGE0_ENABLED` остаётся выключенным по умолчанию.

## 1. HTTP и права доступа

| Endpoint | Контракт |
| --- | --- |
| `POST /api/problems/{id}/submissions` | JWT; `CreateSubmissionRequest`; немедленный `202`, snapshot статуса и `Location: /api/submissions/{uuid}` после сохранения транзакции |
| `GET /api/submissions/{uuid}` | Только владелец; summary, безопасные case summaries, `retryScheduled` и `nextAttemptAt` |
| `GET /api/submissions?page=0&size=20` | История текущего пользователя; до 50 summaries на страницу, без исходников и диагностики |
| `POST /api/problems/{id}/runs` | JWT; sample-only запуск, `202`, `Location: /api/runs/{uuid}`; idempotency key обязателен |
| `GET /api/runs/{uuid}` | Только владелец и только до TTL; остальные пользователи/истёкшие результаты получают 404 |
| `GET /api/teacher/submissions/{uuid}` | Исходник и summary для преподавателя-владельца задачи либо администратора; другой преподаватель получает 404 |
| `GET /api/collections/{id}/progress` | JWT; прогресс текущего пользователя по точным опубликованным версиям members |
| `GET /api/users/me/progress` | Счётчики и SOLVED только из formal submissions/progress; feedback analytics остаются отдельными |

Body submission: `problemVersionId`, необязательный `collectionId`, `languageKey`, `sourceCode`, UUID `idempotencyKey`. Sample Run использует те же поля без `collectionId`. Вход ограничен Jakarta Validation, 20 000 UTF-16 characters и 80 000 UTF-8 bytes из `ContesterLimits`; некорректный UTF-16 отклоняется. Проверяются разрешённая опубликованная версия, операторский runtime и точное членство версии в доступной коллекции.

Idempotency submissions имеет область `(student, key)`, sample runs — собственную `(requester, key)`. Повтор с тем же problem/version/language/collection/source hash возвращает прежний объект, включая состояние после рестарта. Изменённый payload с тем же ключом даёт 409. Идентичный повтор разрешён даже при заполненной очереди. Для sample runs гарантия действует в течение срока хранения записи; после TTL GET возвращает 404, последующая физическая очистка удаляет запись и ключ.

`attemptNumber` отсутствует во входном DTO. Он выделяется только внутри транзакции нового formal submit под короткой PostgreSQL advisory admission-блокировкой и row lock профиля `(student, problem)`. Транзакция сохраняет счётчик, submission, job и обязательные case checkpoints вместе. Повтор POST, отказ quota/validation, sample run, редактирование и старые WS/session-счётчики не создают formal attempt.

Ошибки используют существующий safe envelope `{code, message, requestId}`. Студент не получает чужой source, provider token, test IDs, input/expected output, stdout/stderr/compile output. Диагностика не сохраняется даже при намеренно вредоносном echo в результате порта. Student-facing DTO построены явно; Entity и provider DTO не возвращаются контроллерами. `toString()` source-bearing новых request/detail DTO замаскирован.

## 2. Схема и архитектурные границы

Новая миграция: [V11__durable_judging_and_progress.sql](../../src/main/resources/db/migration/V11__durable_judging_and_progress.sql).

- `execution_case_results.dispatch_state`: `PENDING`, `SENDING`, `TICKETED`, `COMPLETE`; сохраняется original provider deadline.
- `execution_jobs.retry_allowed` и индекс для ограниченных безопасных повторов.
- Sample-run idempotency key и уникальный индекс; старые run rows остаются совместимыми с nullable key.
- `student_problem_progress`: формальные попытки, первое принятое submission, timestamps и реальные test counters; уникальность `(student_id, problem_id)`, optimistic version.
- Backfill использует только `submissions`, никогда `interaction_logs`, legacy session counters или совпадение внешних numeric task IDs.
- PostgreSQL trigger разрешает accepted progress только при совпадении student/problem и наличии FINISHED/ACCEPTED evidence с непустым полностью пройденным набором.

V1–V10 и существующие поля `interaction_logs` не менялись. ML endpoint, PolicyFeatures, модели/метрики и статьи не изменялись. Новые данные не объявляются исследовательскими результатами.

| Компонент | Ответственность |
| --- | --- |
| `ExecutionInputResolver`, `ExecutionAdmission` | Валидация immutable inputs, общая межпроцессная admission-блокировка, quotas для runs/submissions |
| `SubmissionService`, `SampleRunService` | Идемпотентное создание и безопасные owner projections |
| `JudgingStore` | Короткие транзакции claim, heartbeat, checkpoint, retry, expire и atomic finalization |
| `JudgingCoordinator` | Ограниченное число async chains, вызовы порта вне транзакций, управление lease lifecycle |
| `ExecutionResultAggregator`, `ExponentialBackoff` | Детерминированная агрегация и общая логика backoff; Judge0 adapter использует тот же backoff |
| `JudgingEvents` | Advisory process-local события после commit; ошибка listener не откатывает результат |
| `SampleRunCleanup` | Ограниченная очистка истёкших terminal sample runs вместе с jobs/checkpoints |
| `CollectionProgressService`, `UserService` | Реальный прогресс; историческая feedback activity явно отделена от grading |

Все новые dependencies внедряются Lombok `@RequiredArgsConstructor`; модели используют Lombok builders/no-args. Сетевые DTO не зависят от JPA entities.

## 3. Машина состояний и восстановление

```text
QUEUED -> DISPATCHING -> RUNNING -> FINISHED + verdict
                  \          \
                   -> SYSTEM_ERROR
SYSTEM_ERROR + retryScheduled -> DISPATCHING (bounded, original deadline)
expired/exhausted work -> SYSTEM_ERROR, retryScheduled=false
```

1. Scheduler захватывает ограниченную пачку через `ExecutionJobRepository.lockNextJobs` с PostgreSQL `FOR UPDATE SKIP LOCKED`. В той же короткой транзакции обновляет lease owner/token/expiry, generation и attempt count. Возвращает отдельный lease DTO после commit.
2. Каждая запись результата — стабильный checkpoint generation **0**. `execution_jobs.generation` обозначает поколение владельца lease, а не новую отправку студента/повторное исполнение всех тестов. Все изменения checkpoint и финализация проверяют job generation, случайный lease token, lease expiry и общий deadline под row lock.
3. Перед POST фиксируется `SENDING` для выбранной пачки. После ответа фиксируются точное token/case соответствие и original provider deadline; только затем начинается polling. Нет открытой claim/checkpoint-транзакции во время submit/poll.
4. Сохранённые `TICKETED` опрашиваются повторно после рестарта. Уже `COMPLETE` тесты сохраняются; следующие батчи отправляют только оставшиеся `PENDING` тесты. Sample Run выбирает исключительно public samples.
5. `SENDING` без сохранённых токенов означает неизвестный результат POST. Джоба завершается `SYSTEM_ERROR / SUBMISSION_UNKNOWN`, автоматической повторной отправки нет. Это относится и к crash после записи намерения, даже если POST мог ещё не начаться: выбран безопасный отказ.
6. Существующий Judge0 adapter выполняет асинхронный GET polling с экспоненциальным backoff и ограничением числа запросов. Воркера можно безопасно перезапустить для сохранённых токенов; remote POST при этом не повторяется. Общий job deadline и сохранённый provider deadline не продлеваются при recovery.
7. Definitively-not-submitted sandbox failure может вернуть SENDING в PENDING и запланировать ограниченный повтор. Неизвестная ошибка POST не считается доказательством отсутствия исполнения. Ошибка GET допускает повтор только опроса сохранённых токенов.
8. `SYSTEM_ERROR` с `retryScheduled=true` — наблюдаемое состояние ожидания безопасной повторной обработки, не студенческий verdict. Следующее время видно в GET. После исчерпания claim/retry budget либо deadline повтор запрещён; новые запросы студента с новым key являются отдельными formal submissions.
9. Пустой/неполный набор, неизвестные/дублирующиеся test IDs или токены, malformed/противоречивые результаты, незавершённый тест либо инфраструктурный сбой не дают ACCEPTED. Итог считается по всем доверенным cases в порядке версии; все обязательные тесты должны завершиться. Для ACCEPTED каждый verdict должен быть ACCEPTED.
10. Устаревшие callbacks не меняют результат нового владельца. Heartbeat продлевает живой lease; sweeper завершает просроченные/исчерпавшие бюджет jobs, включая orphaned jobs после последнего lease.

Значения времени и памяти в summary — максимум среди завершённых обязательных cases; counts отражают полную suite. Частично завершённые cases сохраняются для recovery, но прогресс не обновляется до полной успешной финализации.

## 4. Прогресс и события

Финализация submission, job и student progress выполняется в одной транзакции. ACCEPTED evidence flush выполняется перед проверкой FK/trigger профиля. Ошибка сохранения профиля откатывает также итоговый verdict; сохранённые ранее результаты позволяют завершить работу новым lease без нового исполнения. Первый accepted submission фиксируется один раз; последующие accepted попытки не увеличивают solvedTasks.

Профиль берёт attemptedTasks/totalAttempts/SOLVED из `student_problem_progress`. Исторические logs и sessions продолжают поддерживать feedback/latency analytics, но не влияют на grading. В ответе есть `progressSource=JUDGED_SUBMISSION`; старая activity помечается `LEGACY_HEURISTIC_ACTIVITY`, известный evidence origin сохраняется. Неизвестные внешние task IDs не сопоставляются с native tasks по численному равенству.

Collection progress использует ACCEPTED submissions текущего пользователя по **точным pinned versions**. Принятое решение другой версии или другого студента не решает member коллекции.

`SubmissionJudged` содержит идентификаторы и status/verdict; публикация происходит после commit. Это локальное advisory event, без гарантии доставки после crash. HTTP polling остаётся durable recovery path. Отправка во внешний WS broker/user destination не включалась: авторизация подписок и WS/ML correlation остаются Step 6.

## 5. Ограничения и настройки

| Переменная | Значение по умолчанию / назначение |
| --- | --- |
| `APP_JUDGING_ENABLED` | `true`; в test profile scheduler выключен, тесты запускают его контролируемо |
| `APP_JUDGING_CONCURRENCY` | `4` локальные async jobs на backend process |
| `APP_JUDGING_BATCH_SIZE` | `5`, не выше configured Judge0 max batch size; несогласованная конфигурация отклоняется при старте |
| `APP_JUDGING_MAX_ATTEMPTS` | `3` lease acquisitions/recovery attempts, не число студенческих попыток |
| `APP_JUDGING_MAX_PER_USER` | `5` outstanding runs + submissions, включая scheduled safe retries |
| `APP_JUDGING_MAX_QUEUED` | `1000` admitted jobs во всех application instances, включая safe retries |
| `APP_JUDGING_LEASE` | `15s`; scheduler tick по умолчанию 1s |
| `APP_JUDGING_DEADLINE` | `5m`, общий deadline job |
| `APP_JUDGING_SAMPLE_TTL` | `15m`; GET после expiry даёт 404 |
| `APP_JUDGING_INITIAL_BACKOFF`, `APP_JUDGING_MAX_BACKOFF` | `1s`, `10s` между безопасными recovery attempts; отдельный GET backoff регулируется Judge0 settings |

Параметры/длительности валидируются при старте. Executor и его очередь ограничены. На каждый cleanup проход удаляется максимум 10 terminal expired sample runs; проход по умолчанию раз в 60s. Физическое удаление best-effort, может отставать при backlog; HTTP TTL применяется сразу. Sweeper terminalizes до 100 просроченных jobs за tick. Quota — admission ceiling, не обещание производительности. Concurrency ограничена на process; несколько instances делят PostgreSQL queue, общий sandbox capacity всё ещё требует настройки/замеров.

Исходник каждого запроса ограничен 20 000 characters / 80 000 UTF-8 bytes, диагностические тела не удерживаются. **Долгосрочное удаление graded submissions/source требует политики учреждения `[AUTHOR INPUT REQUIRED]`; автоматическое разрушительное удаление доказательств прогресса не добавлялось.** Эта часть P5.10 остаётся открытой. Общий durable worker сейчас обслуживает SUBMISSION и SAMPLE_RUN; staff reference verification сохраняет принятый механизм Step 4, который fail-closed истекает по deadline. Generic REFERENCE_VERIFICATION jobs завершаются безопасной ошибкой unsupported mode и не могут выдать publication proof; перенос staff reference work на общий recovery journal остаётся отдельной задачей.

## 6. Проверки и фактические результаты

Команда итоговой проверки:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
bash mvnw -B clean verify
```

**Итог:** `BUILD SUCCESS`, 245 tests, 0 failures, 0 errors, 1 existing opt-in replay experiment skipped. Время полного clean verify — 50.697s. Все существующие JaCoCo gates пройдены.

`jacoco`: line 88.37%, branch 69.71%; `jacoco-core`: line 90.89%, branch 70.52%. Новый PostgreSQL suite: 26 cases; новые after-commit event tests: 3 cases. Артефакты: `target/surefire-reports/`, `target/site/jacoco/`, `target/site/jacoco-core/`; локальный build log: `/tmp/socrates-step5-verify.log`.

Testcontainers используют собственный `postgres:16-alpine`; datasource URL/credentials получены от контейнера, `.env` import выключен. Остальные Spring-тесты используют изолированный H2 test profile. Shared БД 21/22 и production не использовались; миграции выполнялись только на disposable БД. Frontend/ML/paper artifacts не менялись.

Новый `SubmissionJudgingPostgresTest` проверяет:

- 8 конкурентных одинаковых POST → одна запись/attempt; 4 конкурентных новых → непрерывные номера 2–5; шестой outstanding запрос получает 429 без расходования номера;
- две настоящие транзакции: первый worker держит row lock, второй получает другую job через SKIP LOCKED до освобождения первого;
- все 7 execution verdicts, полную suite, complete-only acceptance и safe owner/staff/admin projections;
- crash после intent, восстановление токенов и нескольких батчей, stale owner, heartbeat и retry exhaustion;
- poll outage: только один POST при трёх ограниченных GET/recovery attempts; definitive reject допускает safe retry, ambiguous POST — нет;
- partial infrastructure failure, malformed/missing cases/tokens, пустые checkpoints, deadline без продления;
- фактический tick coordinator и завершение зависшей джобы по deadline;
- rollback финального verdict при ошибке progress write и восстановление без новых sandbox calls;
- sample idempotency/ownership/public-only suite, отсутствие formal attempt, TTL и ограниченную физическую очистку;
- общие per-user/global quotas для sample/submit; collection progress текущего студента;
- запрет forged accepted progress на уровне PostgreSQL и отсутствие двойного solved count.

`JudgingEventsTest` проверяет after-commit delivery, отсутствие события при rollback и безопасную ошибку listener. `UserServiceTest` теперь явно доказывает, что старые успешные эвристические logs и session attempts не решают native problem. PostgreSQL schema tests проверяют V1→V11, повторную Flyway validation, JPA mappings/repositories; существующие adapter/comparator/authoring tests включены в verify.

**Локальные capacity observations:** сценарии 8 duplicate writers, 4 new writers, два независимых DB claimers и quota=1/5 проверены на синтетических fixtures. По итоговому Surefire XML весь сценарий конкурентной идемпотентности занял 0.275s, сценарий SKIP LOCKED — 0.174s (wall time тестов, не latency отдельных HTTP/DB операций). Это проверки корректности admission/concurrency; не sustained throughput, latency SLA, sandbox load test или classroom result. Production arrival rate/provider capacity — `[RESULT REQUIRED]` после отдельного изолированного Judge0/staging.

## 7. Оставшиеся внешние зависимости

Реальный Judge0 endpoint/runtimes/auth и инфраструктурное доказательство изоляции отсутствуют. Без этого новая задача не сможет пройти настоящую staff reference verification и публикацию. Код не делает локальный execution fallback. WS/ML contracts, frontend workspace, hosting/CI/CD и production smoke остаются последующими шагами; в текущей сессии production не менялся.
