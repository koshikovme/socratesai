# Результат 1: DTO-контракты и схема контестера

**Дата:** 2026-10-22.

**Объём:** согласованная часть Step 1 и Step 2 — DTO, Flyway V9, JPA-сущности, репозитории и локальная автоматическая проверка.

**Результат:** реализовано и проверено. Изначально изменения оставлены в рабочей директории; отдельным сообщением от 2026-10-22 пользователь разрешил commit и push этого этапа. Базы `21` и `22` не использовались.

## 1. Основные решения

1. **Problem сохраняет существующую идентичность `Task` / `tasks.id`.** Класс `Task` расширен, отдельная конкурирующая таблица `problems` и вторая JPA-сущность для той же строки не создавались. Публичные новые DTO используют имя `Problem`. Старые `CreateTaskRequest`, `TaskResponse`, task API и существующий сервис продолжают работать.
2. **Версия задачи содержит условия, настройки языков и тесты.** Опубликованная версия и её дочерние записи защищены от изменения триггерами PostgreSQL. Редактирование опубликованной задачи в дальнейшем должно создавать новый draft.
3. **Коллекция хранит порядок и конкретную версию задачи.** Составные внешние ключи исключают ссылку на версию другой задачи; порядок и повторное включение одной задачи ограничены на уровне БД.
4. **Submission отделён от Run и от ML-прогноза.** Формальная попытка хранит неизменяемые исходник, SHA-256-поле, версию задачи, язык, runtime и лимиты. Временный sample run/reference verification представлен `ExecutionRun`, без номера формальной попытки.
5. **Результаты отдельных тестов общие для всех режимов исполнения.** Вместо дублирования `submission_case_results` и аналогов для samples/reference используется `execution_case_results`, связанная с job и конкретным тестом версии. Это уточнение плана сохраняет единый будущий execution pipeline.
6. **User и Role переиспользуются.** Существующие `users` и `UserRole` не заменялись; выдача ролей и изменение регистрации относятся к Teacher API/Security этапу.
7. **Исторические ML-данные не переинтерпретируются.** Числовое совпадение старого `task_id` с `tasks.id` не используется для автоматической привязки логов/сессий.

Новых контроллеров, judge worker, sandbox-провайдера и изменений фронтенда в этом этапе нет. Новые репозитории ещё не заменяют существующие authoring/mentor use cases.

## 2. Зафиксированные DTO-контракты

DTO реализованы Java records с Lombok `@Builder`, Jakarta Validation и раздельными контрактами для студента, автора и внутренних операций. Объёмные ручные конструкторы не добавлялись.

| Область | Контракт | Назначение |
| --- | --- | --- |
| Problem | `ProblemDraftRequest` | `lockVersion`, title/topic/difficulty, statement/formats/constraints, comparison, visibility, languages, tests. |
| Problem | `ProblemLanguageRequest` | Ключ языка, starter code, запрошенные лимиты; без управляемых клиентом provider/runtime IDs. |
| Problem | `ProblemTestCaseRequest` | Позиция, input, expected output, обязательный флаг hidden. |
| Problem | `ProblemResponse`, `ProblemSampleResponse`, `ProblemLanguageResponse` | Студенческое представление опубликованной версии: разрешённые языки и samples; без набора скрытых тестов и reference solution. |
| Problem | `ProblemAuthorResponse`, `ProblemAuthorVersionResponse`, `ProblemAuthorTestResponse` | Авторское представление с ownership, optimistic version, lifecycle, полной версией и hidden tests. |
| Collection | `CollectionDraftRequest`, `CollectionProblemRequest` | Метаданные, `lockVersion`, visibility и упорядоченный массив problem/version references; позиция выводится из порядка массива. |
| Collection | `CollectionResponse`, `CollectionProblemResponse` | Метаданные коллекции и упорядоченный набор закреплённых задач. |
| Submission | `CreateSubmissionRequest` | `problemVersionId`, optional `collectionId`, `languageKey`, `sourceCode`, UUID `idempotencyKey`. |
| Submission | `SubmissionResponse` | Идентификатор, версия/язык/runtime, серверный номер попытки, status/verdict, счётчики и измеренные ресурсы; без исходника и provider diagnostics. |
| Submission | `SubmissionDetailResponse` | Отдельный owner/staff-only контракт с исходником. Авторизация должна выполняться будущим use case/controller. |
| Run | `CreateExecutionRunRequest` | Версия, язык и исходник; обычный студент не выбирает reference-verification mode. |
| ExecutionJob | `CreateExecutionJobRequest` | Внутренний контракт: версия, ровно один submission/run target, mode, deadline и maxAttempts. Не предназначен для прямого student endpoint. |
| ExecutionJob | `ExecutionJobResponse` | Внутренний status DTO без lease token, lease owner и provider token. |

### Правила контракта

- Идентичность пользователя, ownership, номер попытки, hash исходника и факты исполнения определяются сервером, а не полями student request.
- Новая коллекция/задача по согласованному плану использует authenticated visibility; явное значение обязательно в authoring DTO.
- При создании draft клиент передаёт `lockVersion = 0`; при изменении — токен корневой задачи/коллекции из ответа. Будущий use case сравнивает токен и возвращает `409` при конфликте. На уровне JPA уже работает `@Version`.
- Draft может временно содержать пустые списки languages/tests. Публикация должна отдельно требовать валидную среду, sample, hidden tests и reference verification; этот сервис ещё не реализован.
- Пустые строки input/expected output допустимы как реальные тестовые случаи. Дубликаты позиций тестов, ключей языков и задач в коллекции отклоняются в DTO; DB constraints дополнительно защищают уникальность.
- `runtimeKey`, версия runtime, entry point и `runtimeVerified` в ответе — серверные данные. Выбор разрешённых реально установленных runtimes будет реализован вместе с sandbox.
- `ContesterLimits` фиксирует потолки транспортного контракта: source — 20 000 символов, statement — 50 000, test input/output — 65 536, до 100 тестов, до 10 языков, до 200 задач в коллекции. Operator/runtime limits могут быть строже; это не обещание пропускной способности sandbox.
- Максимальные DTO-лимиты: CPU 30 000 ms, wall 60 000 ms, memory 1 048 576 KiB, output 65 536 bytes. Проверяется `wall >= CPU`. Фактические допустимые лимиты и runtime overhead требуют проверки на этапе sandbox.
- Исходник ограничивается символами на DTO/DB уровне; UTF-8 требует до 80 000 байт при этом пределе. Общие HTTP/WS frame/body limits и потоковые ограничения provider response относятся к этапам transport/sandbox.
- Idempotency key находится **в request body**, уникален в пределах студента для formal submissions. Повтор с тем же ключом и изменённым source/version должен стать `409`; SQL unique key уже присутствует, HTTP-use case ещё не подключён.
- `ExecutionStatus`: `QUEUED`, `DISPATCHING`, `RUNNING`, `FINISHED`, `SYSTEM_ERROR`. Verdict появляется только у `FINISHED`; infrastructure error не является неверным решением студента.
- `ExecutionVerdict`: `ACCEPTED`, `WRONG_ANSWER`, `COMPILATION_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`, `OUTPUT_LIMIT_EXCEEDED`.
- `ACCEPTED` не допускается при нулевом наборе, неполном проходе или ненулевом числе неудачных тестов. Эти DB-проверки необходимы, но не заменяют будущую агрегацию доверенных результатов sandbox.
- `OutputComparison.EXACT` нормализует CRLF; `IGNORE_TRAILING_LINE_ENDINGS` дополнительно допускает различие финальных переводов строк. Значимые пробелы сохраняются. Сейчас фиксируется политика; сам comparator относится к sandbox модулю.
- Каталоги и история используют pageable summary queries. Политика будущего HTTP API: страницы с ограниченным `size`, безопасные summary DTO, без source/test blobs; доступ проверяется на уровне use case. Контроллеры и HTTP page/error envelope будут подключаться в Step 4/5.

## 3. Миграция V9

Файл: [V9__create_contester_tables.sql](../../src/main/resources/db/migration/V9__create_contester_tables.sql).

### Новые таблицы и соответствующие JPA-сущности

| Таблица | Entity | Основное содержимое |
| --- | --- | --- |
| `problem_versions` | `ProblemVersion` | Условия, formats/constraints, difficulty, comparison mode, номер и состояние версии. |
| `problem_language_configs` | `ProblemLanguageConfig` | Runtime snapshot, starter, entry point и ограничения языка. |
| `problem_test_cases` | `ProblemTestCase` | Упорядоченные sample/hidden cases и optional ссылка на старый тест. |
| `collections` | `ProblemCollection` | Автор, название/описание, lifecycle, visibility и optimistic lock. |
| `collection_problems` | `CollectionProblem` | Позиция и закреплённая problem/version пара. |
| `submissions` | `Submission` | Студент, snapshot, idempotency/attempt, состояние и агрегаты исполнения. |
| `execution_runs` | `ExecutionRun` | Неградуируемый sample/reference run с TTL и snapshot. |
| `execution_jobs` | `ExecutionJob` | Один target, mode/status, retries, generation, lease, deadline и безопасный error code. |
| `execution_case_results` | `ExecutionCaseResult` | Результат теста определённого job/generation, provider reference, ограниченные диагностика и ресурсы. |

`ExecutionSnapshot` — общий `@Embeddable` для неизменяемых входов submissions/runs. `VersionedEntity` — общий mapped superclass с UTC timestamps и `@Version`. Внешние ссылки между агрегатами хранятся по ID; нужные отношения внутри данных задачи/коллекции/результатов используют lazy JPA associations. Не добавлялись каскадные удаления всей истории.

### Расширение существующих таблиц

- `tasks`: nullable `owner_id`, `status`, `visibility`, `published_version_id`, `updated_at`, `lock_version`. Старые title/language/description/starter/published/created_at сохраняются.
- `student_task_sessions`: nullable native problem/version/language и `context_source`.
- `interaction_logs`: nullable native problem/version/language, `context_source`, `evidence_origin`, `code_revision`, optional `submission_id`.
- Все старые ML-features, `mentor_state`, `mentor_state_confidence`, outcomes и expert-label ссылки сохранены. Менять `/predict-mentor-state`, `PolicyFeatures` или Python-модель для этой схемы не потребовалось.

### Ограничения и целостность

- Версия относится именно к указанной задаче; execution target/case относится к той же версии; выбранный язык существует у версии.
- Не более одного draft на задачу; уникальные номера версий, позиции тестов и membership.
- Уникальная пара student/idempotency key и student/problem/attempt number.
- Job имеет ровно один target правильного mode; активный lease обязателен для `DISPATCHING`/`RUNNING`; retries и counters неотрицательны и ограничены.
- Прикладной worker ещё должен обновлять lease/generation и terminalize просроченные jobs; наличия репозитория недостаточно для работающей очереди.
- Частичный unique index ограничивает активные **явно native** sessions по student/problem/language. Старые неоднозначные sessions и их дубликаты остаются нетронутыми.
- PostgreSQL triggers запрещают update/delete опубликованной версии и изменение/добавление/удаление её language/test definitions. Parent-row locking не даёт publication обогнать изменение дочерней записи.
- Отдельный trigger запрещает изменение source/context/idempotency snapshot формального submission; статусы и агрегаты остаются изменяемыми для будущего worker.
- Диагностика case results ограничена 65 536 bytes на поле. Возврат hidden-dependent stdout/stderr студенту должен быть запрещён будущим API mapper независимо от этого размера.
- Индексы предусмотрены для каталогов, owner/student history, version references, pending queue, expired leases и run cleanup.

Проверки ownership, ролей, допуска runtime, полноты публикации, изменения pinned collection version и отказа в доступе к hidden results относятся к сервисам следующих этапов; JPA-репозиторий сам по себе не является авторизацией.

## 4. Backfill и обратная совместимость

1. V1–V8 не изменены; V9 добавляется в историю Flyway.
2. Для существующих `tasks` создаётся версия 1 с прежними title/topic/difficulty/description. Сохраняются исходные `tasks.id` и все старые `task_tests`.
3. Стартер и язык копируются в language config; `runtime_key = legacy-unverified`, `runtime_version = unverified`, `runtime_verified = false`. Это явная заглушка происхождения, а не подтверждённая среда исполнения.
4. Для snapshot заданы технические начальные лимиты 2000/5000 ms, 262144 KiB и 65536 bytes. До проверки runtime они не дают права запускать legacy problem. Исправление опубликованного legacy snapshot требует нового draft.
5. Новые timestamps обозначают создание snapshot при миграции. Старые `TIMESTAMP` не конвертировались в предположительно известный часовой пояс.
6. Samples/hidden tests копируются в исходном ID-порядке, с `legacy_test_id` для проверки соответствия. Published legacy задачи сохраняют прежнюю public visibility; неполные legacy suites не становятся пригодными для формального judge автоматически.
7. Миграция обрабатывает каталог SQL-пакетами по 500 задач. Она остаётся атомарной Flyway-транзакцией: checkpoints между отдельными commit не реализованы. Для большого production каталога до запуска необходимо отдельно оценить блокировки и время на staging; нулевая остановка не заявляется.
8. Все старые log/session records получают `LEGACY_UNKNOWN`, новые native FK остаются NULL. Даже совпадающий `task_id` не трактуется как доказанная связь. Производственные записи/счётчики сопоставления в этом этапе не исследовались.
9. Новый legacy create через старый сервис остаётся возможен, но автоматическое создание версии для него ещё не подключено. Version-aware authoring/use cases должны стать единственным новым write path в Step 4.

Инвентаризированы legacy ID callers: `TaskSolveView` и profile links; WS/mentor/analyzer DTO; `MentorWorkflowService`, `StudentContextService`, `StudentTaskSessionService`, `InteractionLogService`, `UserService`; SQL/CSV export; `problem_suite_http_benchmark.py`, `real_http_benchmark.py`; связанные backend/frontend тесты. Они не переключались на native контекст преждевременно.

## 5. Репозитории и производительность

- Существующий `TaskRepository` остаётся репозиторием корневого Problem; добавлены owner-scoped lookup и paginated closed projections без statement/starter/tests.
- Для всех новых таблиц добавлены отдельные Spring Data repositories.
- История submissions возвращается конструкторной DTO-проекцией без `source_code`. Owner lookup и idempotency lookup отделены от summary query.
- Members и case-result lists используют целевой `EntityGraph`, чтобы не делать отдельный lazy query на каждую нужную версию/тест.
- `ExecutionJobRepository.lockNextJobs` использует ограниченный `LIMIT` и PostgreSQL `FOR UPDATE SKIP LOCKED`. Требуется внешняя транзакция (`Propagation.MANDATORY`); установка lease/generation должна произойти в ней до commit. Сетевых вызовов в репозитории нет.
- SQL inspection в тесте подтверждает отсутствие source/statement/test blobs в проверенных summary queries.

## 6. Проверка

Проверки проводились только на локальном Docker/Testcontainers PostgreSQL `16-alpine` (фактический сервер 16.14) и существующих изолированных тестовых конфигурациях. Testcontainers 2.0.4 берётся из Spring Boot dependency management. Новые DB-тесты создают datasource исключительно из контейнера, не из `.env` или application datasource. Для полного `verify` требуется Docker; миграционные тесты не скрывают его отсутствие автоматическим skip.

Проверены:

1. Полное применение V1–V9 на пустой PostgreSQL, повторный migrate без изменений и Flyway validation.
2. Переход V8→V9 с native/ambiguous/external legacy IDs, samples/hidden cases, interaction и expert label.
3. Сохранность **всех прежних полей** fixture task/session/log/expert label после миграции.
4. Backfill 503 задач и 1006 тестов через границу пакета; соответствие старых/новых test IDs и expected output.
5. Hibernate `ddl-auto=validate` и bootstrap всех 15 JPA repositories поверх реальной миграции.
6. JPA persist/load новых моделей, snapshots, relation lookups и pageable projections; запрет owner lookup для другого пользователя.
7. SQL constraints на несовместимые problem/version/case references, idempotency/attempt duplicates, неполный accepted, counts и некорректный job target/lease.
8. Immutable published versions/children и submission source; optimistic-lock конфликт редактирования коллекции.
9. Partial uniqueness native sessions при сохранении legacy duplicates.
10. Два конкурентных захвата jobs через разные транзакции с `SKIP LOCKED`; запрет вызова без транзакции.
11. DTO validation, source limits, nested validation, duplicate membership/positions/languages, modes и deadlines; JSON contracts без internal lease/provider fields и без hidden-test контейнера в student problem DTO.
12. Существующие backend регрессии, включая task/mentor/ML selector/logging/session/API tests.

Выполненная итоговая команда:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
    bash mvnw -B verify
```

**Итог:** BUILD SUCCESS; 94 теста, 0 failures, 0 errors, 1 skipped. Из них новые: 5 `ContesterDtoContractTest` и 8 `ContesterSchemaPostgresTest`, все 13 прошли. Пропущен существующий opt-in `MentorPilotReplayExperimentTest`, который требует отдельного включения экспериментов. Оба существующих JaCoCo gates пройдены; пороги не снижались. Полное 100% покрытие проекта не заявляется.

Артефакты локального запуска: `target/surefire-reports/`, `target/site/jacoco/`, `target/site/jacoco-core/`, собранный backend JAR в `target/`. Это проверки схемы/контрактов, не новый ML-эксперимент, benchmark или доказательство учебной эффективности. Fixture data синтетические; seed не применяется.

## 7. Что остаётся следующим этапам

- Завершить не относящиеся к схеме решения Step 1: реальные runtime versions, провайдер/хостинг/домен/бюджет, retention, WS protocol/correlation.
- Реализовать authoring/use cases и безопасные controllers: student-only registration, role provisioning, ownership, publication verification, version-aware writes, ошибки/HTTP pagination.
- Добавить sandbox adapter и queue worker с lease fencing, безопасной агрегацией и timeout recovery; не исполнять пользовательский код в backend.
- Подключить native session creation и атомарные counters. Наличие partial index не означает, что текущий legacy session service уже переписан.
- Выполнить осмысленную инвентаризацию legacy mappings и rehearsal rollback на отдельном staging с разрешёнными данными. Старые image rollback и production backfill здесь не запускались.
- Перевести WS/mentor/progress/frontend на новый контекст и реальные verdicts, затем CI/CD/hosting по плану.

Python ML endpoint, модель, метрики и policy-action features не менялись; связанных изменений текста статьи этот этап не требует. Другие research/paper artifacts не редактировались.

На этапе реализации изменения были оставлены unstaged без commit/push. Последующее явное разрешение пользователя от 2026-10-22 позволяет опубликовать этот этап вместе с тестами и документацией; посторонние исследовательские файлы не включаются.
