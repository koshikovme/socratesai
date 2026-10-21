# Step 3 — Isolation Sandbox: адаптер и локальная проверка

**Дата:** 2026-10-22. **Результат:** запрошенные контракты, Judge0-адаптер, асинхронный polling, компаратор и безопасная нормализация реализованы и проверены локально. Инфраструктурный exit всего Step 3 остаётся открытым: пользователь подтвердил отсутствие отдельного Judge0-сервера. Реальное исполнение, проверка изоляции и production deployment не проводились. По умолчанию интеграция выключена.

## Реализованная архитектура

| Слой | Изменение |
| --- | --- |
| Domain | `CodeExecutionPort`, входной `ExecutionBatch`, лимиты и тесты; `SubmittedExecutionBatch` с case/token mapping и исходным deadline; case/batch results, diagnostics, безопасные failure codes |
| Domain | `OutputComparator` и `ExecutionResultAggregator`, независимые от Spring/HTTP/JPA |
| Infrastructure | `Judge0ExecutionAdapter`, отдельные wire DTO, ограниченный HTTP client, runtime registry, request validator, result mapper, backoff и async runtime |
| Application / public DTO | `StudentExecutionResultMapper`, `StudentExecutionResultResponse`, `SampleExecutionResultResponse`; hidden cases полностью исключены из деталей |
| Configuration | `app.execution.judge0.*` / `APP_JUDGE0_*`, bounded executors, startup validation; enabled=false, endpoint/key/allowlist отсутствуют |

Контракты с исходным кодом, stdin, expected output, токенами и диагностикой — внутренние. Их нельзя возвращать из student endpoints. Публичный mapper имеет отдельный тип результата. Метаданные trusted tests/runtime должен предоставлять будущий application use case из закреплённой версии задачи.

`submitBatch` сначала проверяет запрос и операторский runtime allowlist через `/languages`, затем делает один batch POST с Base64 source/stdin. `expected_output`, произвольные compiler options, attachments и callbacks не отправляются. ID языка, runtime key/version и полное имя каталога сверяются; публичные demo IDs не зашиты в production configuration. Каталог кешируется с ограниченным TTL.

`pollBatch` принимает уже полученные tickets, сопоставляет ответы по токенам, опрашивает только незавершённые cases и сохраняет завершённые результаты. Разделение submit/poll позволяет Step 5 сохранить tickets в БД до ожидания результата. В этом этапе очереди, транзакционный worker, HTTP execution endpoints и WS/ML execution pipeline ещё не подключены.

## Асинхронность и сбои

- CompletableFuture + JDK asynchronous HTTP + ScheduledExecutorService; нет блокирующего ожидания или sleep в production polling.
- Backoff удваивается до верхней границы; pending statuses и повторяемые GET-ошибки используют общий счётчик попыток. Повторяются только polling GET при 429/5xx/transport errors.
- Абсолютный deadline ограничен operator operation timeout и сохраняется после создания батча. Polling не начинает новый полный timeout. Независимый deadline останавливает и зависшее чтение response body.
- Ограничены concurrent local operations, HTTP executor/queue, batch size, poll attempts, тела HTTP до JSON parsing, глубина/строки/числа JSON и decoded output. Завершение/отмена освобождают permits, HTTP futures и таймеры.
- Создание батча автоматически не повторяется. Partial/malformed token responses и неопределённые POST failures дают безопасную ошибку с `mayHaveBeenSubmitted=true`; definitive 4xx кроме 408 — false. Неопределённый результат не считается студенческой ошибкой.
- При истечении deadline итог всегда `SYSTEM_ERROR`, даже если callback уже получил все cases и ещё агрегирует их. Завершённые case results сохраняются, общий verdict остаётся пустым. Отдельный тест воспроизводит эту гонку через управляемый latch.
- Локальная отмена не доказывает остановку удалённой программы. Provider execution limits и cleanup обязательны отдельно. Partial create может оставить ограниченную удалённую работу без полного набора tickets; durable reconciliation относится к Step 5.

## Ресурсы и сравнение

Сохраняется существующий source ceiling 20 000 Java characters; дополнительно проверяются корректность UTF-8 и byte limits. Настройки по умолчанию: 80 000 source bytes, 65 536 bytes на stdin/expected output, 5 cases/batch, 16 активных local operations, 2 MiB HTTP response, 100 poll attempts, 100 ms → 2 s backoff, 30 s operation timeout. Это технические ограничения, а не измеренная пропускная способность хоста.

В provider request явно задаются CPU/wall limits в секундах, memory/stack в KiB, process/thread cap, file limit в KiB с округлением вверх, aggregate time/memory flags, network=false, number_of_runs=1 и separate stderr. После Base64 stdout/stderr ограничиваются точным output byte limit; compile diagnostics — 64 KiB. Реальная поддержка этих ограничений, compile-specific resource settings и network/filesystem/process isolation требуют проверки отдельного сервера.

`EXACT`: CRLF → LF, затем точное сравнение, включая final LF. `IGNORE_TRAILING_LINE_ENDINGS`: та же нормализация, затем удаление только конечных LF с обеих сторон. Пробелы, tabs, lone CR, внутренние пустые строки, Unicode и регистр сохраняют значение. Глобального trim нет.

Нормализация статусов:

| Provider ID | Внутренний результат |
| --- | --- |
| 1 / 2 | QUEUED / RUNNING, verdict отсутствует |
| 3 | FINISHED; ACCEPTED только после локального сравнения |
| 4 / 5 / 6 | WRONG_ANSWER / TIME_LIMIT_EXCEEDED / COMPILATION_ERROR |
| 7, 9–12, 14 | RUNTIME_ERROR |
| 8 | OUTPUT_LIMIT_EXCEEDED |
| 13 | SYSTEM_ERROR / PROVIDER_INTERNAL_ERROR |
| Неизвестный/отсутствующий status, невалидный протокол | SYSTEM_ERROR, без student verdict |

В документированном Judge0 CE нет отдельного memory-limit status. MLE выводится только из измеренного превышения лимита, с сохранением явных time/compilation/file failure verdicts; student stderr/messages не используются как доверенная классификация. OOM без достаточного измерения остаётся runtime error. Переполнение decoded output не превращает compilation/runtime/time failure в другой verdict. Malformed Base64 даёт protocol failure, invalid UTF-8 у normal output — wrong answer. Неизвестные/отрицательные/переполняющие метрики отвергаются; отсутствующие остаются NULL. Агрегатор выдаёт ACCEPTED только для полной непустой принятой batch suite; worker обязан отдельно проверить полную suite нескольких батчей.

## Безопасность и совместимость

- Удалено использование `javax.tools`, `ToolProvider` и in-process JavaCompiler из `SyntaxCheckEngine`. Нет локального process/compiler fallback.
- Legacy analyzer оставляет ограниченную статическую эвристику для обратной совместимости: `compilerExecuted=false`, `testsExecuted=false`, `evidenceOrigin=STATIC_HEURISTIC`. Поля `compileSuccess` и test counts пока являются оценками; они не доказывают выполнение. Полная nullable/provenance адаптация относится к Step 6.
- Hidden stdout/stderr/compile output отбрасываются после внутреннего сравнения. Public mapper дополнительно исключает hidden cases, включая IDs, metrics и diagnostics. Тест с эхом hidden input подтверждает отсутствие этих данных в student JSON.
- Source/stdin/expected/tokens/diagnostics исключены из generated toString. Sandbox exceptions содержат только фиксированные коды, без provider bodies и исходных causes. HTTP redirects запрещены; secret-bearing endpoint configuration не включается в ошибки.
- Из ML HTTP/JSON ошибок удалены response body/исходные причины; неизвестное action не отражается в exception. Fallback logger пишет только тип исключения; log capture проверяет отсутствие secret message/stack trace.
- При включённом адаптере запрещён JDK HTTP wire logging. Proxy/APM/access logging с token query strings необходимо проверить на отдельном хосте.
- Flyway V1–V9, interaction_logs и прежние ML fields не изменены. PolicyFeatures JSON shape, `/predict-mentor-state`, confidence fallback, ML модели/метрики не менялись. Старые ML/log и PostgreSQL тесты проходят.

Проверены затрагиваемые ML-контракты и основной IEEE integration section. Этот delivery не добавляет доказательств code-and-run runtime integration: существующие execution-enriched benchmark результаты остаются offline evidence; publication description двух feature modes нельзя использовать как подтверждение работающего judge. Полное подключение execution evidence остаётся Step 6. Бумаги, их PDF/копии и experiment artifacts этим изменением не редактировались; новых исследовательских результатов не заявляется.

## Автоматическая проверка

Команда после последнего изменения application code и tests:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:/usr/local/bin:/usr/bin:/bin \
  bash mvnw -B verify
```

**Результат: BUILD SUCCESS; 178 tests, 0 failures, 0 errors, 1 existing opt-in replay experiment skipped.** Собран Spring Boot JAR, пройдены существующие global/core JaCoCo gates без снижения порогов. Точные coverage counters доступны в generated reports.

79 новых sandbox test cases, включая 26 real localhost HTTP cases, покрывают comparator, status mapping, metrics/decoding bounds, hidden privacy, allowlist/config validation, backoff saturation, cancellation/capacity cleanup, stalled/chunked responses, redirects, retry policy, uncertain POST outcomes и deadline/aggregation race. Ещё 5 новых regression cases проверяют non-executing legacy path и ML error/log redaction; существующие analyzer tests дополнены evidence assertions.

Использованы только JDK HttpServer protocol fixtures без исполнения student source, H2 и существующие Testcontainers integration tests на disposable PostgreSQL `postgres:16-alpine`. Общие БД 21/22 и production data не использовались. Warning о существующих Lombok builder defaults в StudentTaskSession и Mockito dynamic attachment не является новым failure.

Generated artifacts: `target/surefire-reports/`, `target/site/jacoco/`, `target/site/jacoco-core/`, `target/socratesai-0.0.1-SNAPSHOT.jar`. Они не добавляются в Git. Конфигурация и integration instructions: [docs/sandbox.md](../../docs/sandbox.md).

## Feasibility: подтверждено и что остаётся

Проверены первичные [Judge0 API docs](https://ce.judge0.com/), [release notes](https://github.com/judge0/judge0/releases) и [security advisories](https://github.com/judge0/judge0/security/advisories). Upstream 1.13.1 содержит исторические исправления critical escapes; это не разрешение произвольного текущего образа. Self-hosted кандидат требует отдельного execution host и проверки kernel/cgroup/privileges; такие изменения на рабочей машине не выполнялись. Managed provider остаётся альтернативой после проверки privacy, retention, регионов, ограничений и цены. Ни один вариант не закуплен и не развёрнут.

| Недостающий артефакт | Статус |
| --- | --- |
| Isolated endpoint / auth / host / network boundary | [AUTHOR INPUT REQUIRED] — сервера нет, подтверждено пользователем |
| Approved provider image digest / patch provenance | [VERIFY FROM ARTIFACT] — deployment отсутствует |
| Installed compiler IDs/versions, entry points, starter checks | [RESULT REQUIRED] — fixtures не являются реальными runtimes |
| Compile/runtime/network/filesystem/process/memory/cleanup probes | [RESULT REQUIRED] — только на disposable sandbox |
| Measured overhead, production quotas, budget/region/retention | [RESULT REQUIRED] / [AUTHOR INPUT REQUIRED] |

В work-notes закрыты P3.3/P3.5/P3.7/P3.12. P3.1/P3.4/P3.6/P3.10 отмечены частично выполненными и остаются открыты; P3.2/P3.8/P3.9/P3.11 требуют реального хоста. Публичное выполнение кода включать до прохождения этих проверок и durable orchestration нельзя.
