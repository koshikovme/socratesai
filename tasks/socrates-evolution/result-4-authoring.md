# Step 4 — безопасное авторство, роли и коллекции

**Дата:** 2026-10-22. **Статус:** запрошенная backend-логика реализована; локальные проверки пройдены. При первоначальной сдаче изменения оставлены **без commit/push**. После отдельного запроса пользователя разрешена публикация этого результата в Git. Базовый HEAD — `dba30e7`.

Реальный Judge0-сервер по-прежнему отсутствует. Интеграционные тесты используют управляемую реализацию `CodeExecutionPort`; production-адаптер не подменяется и остаётся выключенным по умолчанию. Новая публикация требует успешной проверки через execution port; обхода при недоступной песочнице нет. Проверка compiler/runtime isolation остаётся задачей Step 3.

## 1. Регистрация и административные роли

- Публичная регистрация создаёт только `STUDENT`, в том числе при отсутствии/null роли. Запрошенные `TEACHER`/`ADMIN` отклоняются с 400; неизвестные enum-значения также дают безопасный 400. Ограничение проверяется DTO validation и повторно в `AuthService`.
- Добавлены ограничения email/fullName, маскирование password/token в generated `toString()`. Селектор привилегированной роли удалён из формы регистрации.
- `PATCH /api/admin/users/{id}/role` доступен только администратору. Body: `expectedRole`, `role`, `reason` (обязательная непустая строка до 500 символов). Несовпадение текущей и ожидаемой роли даёт 409; повтор без изменения роли возвращает текущее состояние.
- Изменение роли и audit insert выполняются в одной транзакции. Аудит хранит actor/target, старую/новую роль, причину и UTC timestamp. При сбое аудита роль откатывается. UPDATE/DELETE audit rows запрещены PostgreSQL trigger; это не защита от привилегированного владельца всей БД.
- Строка `administration_state` блокирует конкурирующие role changes/bootstrap. Нельзя удалить последнего администратора, включая конкурентные запросы. Роли загружаются из БД при каждом JWT-запросе; старый JWT не сохраняет отозванные полномочия.
- `CurrentActor` централизует проверку текущего пользователя, staff/admin и владельца. Чужие приватные ресурсы скрываются через 404; отсутствие роли staff/admin даёт 403.

Bootstrap первого администратора — однократный `ApplicationRunner`, выключенный по умолчанию. Внешняя конфигурация:

```text
APP_ADMIN_BOOTSTRAP_ENABLED=true
APP_ADMIN_BOOTSTRAP_EMAIL=<new operator-controlled address>
APP_ADMIN_BOOTSTRAP_PASSWORD=<external secret>
APP_ADMIN_BOOTSTRAP_FULL_NAME=<operator name>
```

Пароль не имеет значения по умолчанию: минимум 16 Java characters, максимум 72 UTF-8 bytes для BCrypt. Существующий email нельзя захватить/повысить через bootstrap. Успешное создание и `BOOTSTRAP` audit атомарны; повторный старт не сбрасывает пароль. Наличие администратора до V10 закрывает bootstrap при миграции. После первичного создания оператор выключает bootstrap и удаляет одноразовый секрет из runtime configuration. Внешние значения и рабочая БД в этой сессии не изменялись.

## 2. Архитектура и миграция

| Область | Основные компоненты |
| --- | --- |
| API safety | `ApiExceptionHandler`, `ApiErrorCode`, `ApiError`, `ApiErrors`; безопасные 401/403 и для security filters |
| Identity/provisioning | `CurrentActor`, admin bootstrap/role services и controller, audit repositories |
| Problems | Teacher/Student controllers/services; `ProblemAccess`, `DraftContentWriter`, `ProblemDtoMapper`, `AuthoringRuntimeResolver` |
| Publication | `ProblemFingerprint`, `PublicationGate`, persisted `ReferenceVerification`, owner-only verification controller |
| Execution boundary | `ReferenceExecutionPort`, bounded batch coordinator поверх существующего `CodeExecutionPort`; повторно используется deadline/cancellation runtime Step 3 |
| Collections | Teacher/Student controllers/services, ownership policy, membership writer и отдельные projections |

Новая Flyway-миграция [V10__secure_authoring_and_role_audit.sql](../../src/main/resources/db/migration/V10__secure_authoring_and_role_audit.sql):

1. Создаёт одноразовое bootstrap state, append-only role audit и таблицу reference verification.
2. Добавляет `problem_versions.visibility` и `reference_verification_id`. Composite FK гарантирует принадлежность verification той же версии задачи.
3. Добавляет уникальность активной проверки версии и ограничения terminal/verdict/count/deadline.
4. Сохраняет неизменяемость опубликованных V9 snapshots: при backfill visibility обновляются только draft rows; старые published snapshots остаются legacy/unverified.

V1–V9, `interaction_logs`, ML feature names, `/predict-mentor-state`, модели/метрики и исследовательские артефакты не менялись. PostgreSQL-тест подтверждает сохранность прежних interaction/expert-label данных после V8→V9→V10. Реальные shared/production миграции не выполнялись.

## 3. Контракты HTTP

Все мутации требуют валидного JWT и соответствующей роли. `lockVersion` — токен **корневой задачи/коллекции**, полученный из author response; при создании передаётся 0. Конкурентное изменение возвращает 409. Jakarta Validation проверяет вложенные DTO, размеры списков/строк, позиции, уникальность языков/members и limits из `ContesterLimits`; дополнительно проверяются UTF-8 и byte ceilings.

| Маршруты | Поведение |
| --- | --- |
| `POST /api/teacher/problems` | Создать owned draft из `ProblemDraftRequest`, 201 |
| `GET /api/teacher/problems[?page=&size=]`, `GET .../{id}` | Собственный каталог/детали; admin видит весь каталог |
| `PUT .../{id}/draft` | Атомарно заменить содержимое текущего draft |
| `POST .../{id}/draft` | Создать draft-клон опубликованной версии или вернуть существующий; body `{lockVersion}` |
| `DELETE .../{id}/draft?lockVersion=` | Удалить допустимый неактивный draft; опубликованный snapshot сохраняется |
| `DELETE .../{id}?lockVersion=` | Удалить только допустимую никогда не опубликованную задачу без внешних ссылок |
| `POST .../{id}/verify` | Запустить staff reference verification, 202 |
| `GET .../{id}/verifications/{verificationId}` | Owner/admin-only безопасный статус проверки |
| `POST .../{id}/publish` | Атомарная публикация; body `{lockVersion, verificationId}` |
| `POST .../{id}/archive` | Скрыть задачу из student API, сохранив историю; body `{lockVersion}` |
| `POST/GET /api/teacher/collections`, `GET/PUT/DELETE .../{id}` | CRUD своих коллекций; admin доступ ко всему каталогу |
| `PUT .../{id}/problems` | Replace/reorder membership; body `{lockVersion, problems:[{problemId,problemVersionId}]}` |
| `POST .../{id}/publish`, `POST .../{id}/archive` | Проверка состава/visibility и lifecycle; body `{lockVersion}` |
| `GET /api/problems`, `GET /api/problems/{id}`, `GET .../{id}/versions/{versionId}` | Опубликованные проверенные summaries/detail; поддержка pinned historical versions |
| `GET /api/collections`, `GET /api/collections/{id}` | Опубликованные разрешённые коллекции с валидными pinned members |
| `GET /api/languages` | Операторский configured catalog; не liveness/изоляционный сертификат сервера |
| `PATCH /api/admin/users/{id}/role` | Audited role provisioning |

Каталоги ограничены `size=1..50`, `page=0..10000`, default size=20, порядок ID descending. Студенты и другие авторизованные пользователи видят `PUBLIC` и `AUTHENTICATED` опубликованный контент; anonymous — только `PUBLIC`. Приватные/архивные/непроверенные записи дают 404 и отсутствуют в student lists.

Коллекции закрепляют конкретную опубликованную версию. Все members проверяются до изменения; позиции задаются порядком массива и сохраняются как 0..N−1 в одной транзакции. `@Version`, ожидаемый revision и короткая блокировка корня предотвращают lost updates. Public collection не может содержать authenticated-only member. Пустую коллекцию нельзя публиковать, опубликованную нельзя очистить полностью; archived collection нельзя редактировать, опубликованную/архивную нельзя физически удалить. При архивировании/закрытии доступа к member коллекция скрывается из student API, сохраняя закреплённые ссылки.

Студенческое чтение выполняется в repeatable-read snapshot. Collection summaries/members выбираются отдельными ограниченными projection queries без загрузки условий всех member-задач, source blobs или hidden suites.

## 4. Staff reference verification и публикация

`POST /api/teacher/problems/{id}/verify` принимает:

```json
{
  "lockVersion": 0,
  "solutions": [
    {"languageKey": "python3", "sourceCode": "<staff reference source>"}
  ]
}
```

Нужен ровно один эталон для **каждого** языка draft. Версия должна содержать непустые условия, минимум один Sample, минимум один Hidden и настроенные runtime mappings. Запрос от другого преподавателя, неподдерживаемый runtime, неполная suite или неправильные limits не запускают исполнение.

Последовательность:

1. В короткой транзакции блокируется owned root, проверяется revision, создаётся execution snapshot и persisted verification с SHA-256 хешем точного содержимого версии.
2. Хеш включает identity/version, условия/formats/constraints, visibility/comparison, runtime key/version/entry point/starter/limits и упорядоченные ID/positions/input/expected/hidden tests. `runtimeVerified` и lifecycle-поля не входят в хеш, чтобы publication metadata не меняла проверенное содержимое.
3. Только после commit bounded dispatcher запускает проверку вне транзакции запроса. Батчи выполняются последовательно через `submitBatch`/`pollBatch`; сохранение статуса выполняется в отдельных коротких транзакциях. Тест проверяет отсутствие активной DB transaction при вызове execution port.
4. Входящий результат сверяется с полным набором case IDs/positions/visibility. Одного агрегированного `ACCEPTED` недостаточно. Принимается только завершённая suite всех настроенных языков; неизвестный, неполный или ошибочный ответ не становится успешной проверкой.
5. Хранятся aggregate status/verdict/counts/safe failure code и срок действия. Source и diagnostics проверки остаются в ограниченном process-local snapshot; в БД и HTTP-status DTO они не сохраняются. Даже sample diagnostics эталона отбрасываются.
6. Публикация повторно проверяет текущий hash, принадлежность verification, `FINISHED/ACCEPTED`, TTL, sample/hidden и runtime configuration. Затем runtime flags, immutable version publication и published pointer корня фиксируются одной транзакцией. Изменение draft после проверки требует новой verification.

Конфигурация:

| Переменная | Default |
| --- | --- |
| `APP_REFERENCE_VERIFICATION_TIMEOUT` | 30 s, общий deadline всей проверки |
| `APP_REFERENCE_VERIFICATION_EVIDENCE_TTL` | 30 min |
| `APP_REFERENCE_VERIFICATION_MAX_CONCURRENT` | 4 |
| `APP_REFERENCE_VERIFICATION_BATCH_SIZE` | 5, не больше Judge0 max batch |

Dispatcher имеет два потока и очередь 16; активные asynchronous operations дополнительно ограничены admission slots. Backpressure/timeout/cancellation приводят к безопасному `SYSTEM_ERROR`, а не к publication bypass. Runtime catalog теперь требует одно операторское сопоставление на language key и допускает configured entry point.

Это scoped reference coordinator, а не общий durable worker Step 5. При рестарте process-local незавершённая работа не воспроизводится автоматически; persisted pending record истекает по deadline при чтении/повторном запросе и не может разрешить публикацию. Универсальный claim/lease/recovery, durable provider tickets и student submissions остаются Step 5. Автоматического повторного POST исполнения после неопределённого provider outcome нет. Реальные runtime overhead/capacity/изоляция требуют отдельного Judge0 host.

## 5. Privacy, Markdown и legacy compatibility

- Author DTO доступны owner/admin и содержат тесты черновика. Student DTO — отдельные типы, без private reference source, hidden cases/IDs, owner metadata, provider tokens, verification records, stdout/stderr/compiler diagnostics и consumed sandbox metrics.
- Student sample query выбирает только `hidden=false`; samples нумеруются отдельно 0..M−1, не раскрывая позиции скрытых cases. Language projection содержит public starter/entry-point/задачные limits; internal runtime mapping/verification flags остаются в author projection.
- `SafeMarkdown.vue` выводит содержимое через Vue text interpolation с сохранением переносов. Условия/Markdown отображаются как экранированный текст; HTML/Markdown links не создают DOM-элементов. Component подключён к текущему solver, проверен на script/img/svg/event-handler/javascript-link payloads. Rich HTML rendering не добавлялся.
- Safe error envelope: `{code, message, requestId}`. Сообщения принадлежат enum, request ID генерирует сервер. Validation errors не возвращают rejected values; SQL/исходные exception messages/causes, source и JWT в ответ не включаются. Это распространяется на malformed JWT и filter-level 401/403.
- Legacy `/api/tasks` использует те же native services и ownership checks. Прямой `published=true` отклоняется; `published=false` создаёт native draft. Legacy public reads ограничены проверенными разрешёнными published snapshots, sample-only projection и первыми 50 entries.
- Старые `legacy-unverified` задачи не становятся автоматически пригодными для нового student catalog. Admin должен создать/исправить draft, настроить runtime, проверить эталон и опубликовать новую native version. Historical IDs/logs сохраняются.
- Текущий frontend external LeetCode catalog не переключался: его замена и полноценные teacher/student screens остаются Step 7. Новые native API и compatibility facade готовы; P4.9 отмечен частично выполненным.

## 6. Проверки и воспроизводимость

Backend — после последнего изменения кода:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
  bash mvnw -B clean verify
```

**BUILD SUCCESS: 216 tests, 0 failures, 0 errors, 1 existing opt-in replay experiment skipped.** Собран backend JAR. Global JaCoCo: lines **87.16%**, branches **70.04%**; core: lines **89.84%**, branches **71.09%**. Существующие пороги не снижались; clean run удалил данные предыдущих instrumented classes.

Новые проверки: 27 PostgreSQL HTTP/security scenarios + 4 bootstrap unit cases + 6 reference coordinator cases + 1 PostgreSQL bootstrap/audit case = **38 новых backend test cases**. Existing legacy tests приведены к обязательному запрету публичного staff signup/direct publication; persistence/publication проверяются настоящим native use case на PostgreSQL.

Проверены Teacher A/B/Student/Admin/Anonymous, stale JWT roles, concurrent last-admin/reorder, audit rollback, ownership на legacy/new routes, missing sample/hidden/languages, wrong/unknown/expired/foreign verification, edit-after-verify, multi-language/multi-batch completeness, immutable published snapshot при clone/edit, hidden privacy, sample ordinals, pinned versions, archive/delete, invalid membership/visibility/limits и safe errors.

Flyway/JPA и native HTTP flow используют только disposable Testcontainers `postgres:16-alpine` (наблюдался PostgreSQL 16.14). Старые быстрые H2 tests сохранены. Нет обращения к БД 21/22, production data или live Judge0; student source не компилировался на рабочей машине. Synthetic port verdicts доказывают application gating, а не фактическое исполнение/host isolation.

Frontend, Node **22.23.1**, из `socratesai-frontend/`:

```bash
env PATH=/home/koshikovalimzhan/.nvm/versions/node/v22.23.1/bin:/usr/local/bin:/usr/bin:/bin npm ci --no-audit --no-fund
env PATH=/home/koshikovalimzhan/.nvm/versions/node/v22.23.1/bin:/usr/local/bin:/usr/bin:/bin npm test
env PATH=/home/koshikovalimzhan/.nvm/versions/node/v22.23.1/bin:/usr/local/bin:/usr/bin:/bin npm run build
```

**15/15 tests passed**, включая 2 новых SafeMarkdown cases; production build успешен. Lockfile/dependency versions не менялись. Сохранилось предупреждение о размере Monaco chunk; настройки bundling не менялись.

Артефакты: `target/surefire-reports/`, `target/site/jacoco{,-core}/`, `target/socratesai-0.0.1-SNAPSHOT.jar`, frontend `dist/` — generated/ignored, не часть исходного изменения. `git diff --check` проходит. При первоначальной сдаче index был пуст и commit/push не выполнялись; последующий запрос пользователя разрешил публикацию Step 4. Посторонние research/untracked файлы не входят в эту публикацию.

Для механизмов authorization, locking и after-commit использованы первичные справочники: [Spring method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html), [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [Spring transaction events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html). Подтверждение работы здесь — локальные тесты указанных версий проекта.
