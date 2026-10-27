# Step 9 — локальное демо; production отложен

Дата проверки: **2026-10-27**. Пользователь выбрал «сначала локальная демонстрация». Результат этой итерации — работающий локальный стенд **http://localhost:8088**, а не размещение в интернете. Хостинг, домен, SSH/deploy identity и отдельный Judge0 не предоставлены. Затрат на облачные ресурсы, регистрации доменов и выдачи публичных сертификатов не было.

## Что работает

- `bash scripts/demo/demo.sh up` поднимает отдельный проект `socrates-local-demo`: production-образы приложения из Step 8, собственный PostgreSQL и дополнительный закрытый имитатор Judge0 API. Frontend-образ пересобран с исправлением выхода из сессии.
- Публикуется только **127.0.0.1:8088**. БД, ML, backend и имитатор не имеют внешних портов. Используются non-root, read-only rootfs, ограничения памяти/CPU/PID и именованный том `socrates-local-demo_postgres_data`. `down` сохраняет том; root `.env` не используется и не изменяется.
- Секреты создаются CSPRNG ОС через Python `secrets`, передаются окружением и сохраняются исключительно в игнорируемой `.delivery/demo/` (директория 0700, файлы доступа/окружения 0600). Физический аппаратный генератор/HSM в этой итерации не сертифицировался. Паролей и JWT в отчёте/коммите нет.
- Настоящие native API: bootstrap администратора, регистрация STUDENT, аудируемое назначение TEACHER, draft с Sample/Hidden, верификация трёх эталонов через `CodeExecutionPort`, публикация и коллекция. Отдельный probe-аккаунт проверяет Run/Submit для python3/java/cpp17; учебный `student` остаётся для ручной демонстрации.
- Настоящий UI: регистрация, каталог, Monaco, авторизованный SockJS/STOMP, ответ ментора, Run/Submit, скрытые метрики без вывода, ACCEPTED-derived профиль и logout. LLM-провайдеры отключены; служба сохранённых моделей готова, обе модели загружены.

**Граница доказательства:** имитатор узнаёт точные шаблоны из `deploy/demo/programs.json` и вычисляет синтетическую сумму для входа из двух целых чисел. Исходный код не запускается/не компилируется. Другой код получает демонстрационный WRONG_ANSWER; произвольные решения и реальные компиляторы не проверены. Время/память фиксированные и не являются измерениями исполнения. В UI есть постоянный баннер об имитации. Каталог language IDs вымышленный и предназначен только для этого fixture. HTTP на loopback не является HTTPS/WSS production acceptance.

## Проверки и воспроизведение

Команды из корня, кроме явно указанного frontend-каталога:

| Проверка | Команда / наблюдение | Результат |
| --- | --- | --- |
| Старт, native provisioning/reference/collections/прогресс | `bash scripts/demo/demo.sh up` | Пять сервисов healthy, API probes на трёх языках прошли |
| Сохранение стенда | `bash scripts/demo/demo.sh down`, затем `up` | Аккаунты/задача/коллекция/прогресс сохранились; API probes прошли снова |
| Миграции новой локальной БД | `flyway_schema_history` | 12 успешных миграций |
| Права роли приложения | `pg_roles`, `pg_stat_activity` собственной БД | demo_contester, SUPERUSER/CREATEDB/CREATEROLE = false; backend соединения под этой ролью |
| Модели | private `/ready` | action_model_loaded и mentor_state_model_loaded = true |
| Fixture-контракты | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests/demo` | 5 passed |
| Frontend unit/regression | `npm test` в `socratesai-frontend/`, Node 22.23.1 | 84 passed, 20 файлов |
| Линт | `npm run lint` в frontend | passed |
| Production frontend build | `docker build -t socratesai/frontend:delivery-test socratesai-frontend` | passed, внутри выполнен Vite production build |
| Реальный browser journey | `npm run demo:e2e` в frontend, локальный Chrome | 1 passed, без page/console errors; HTTP/WS не подменяются |
| Browser regression предыдущего шага | `npm run test:e2e` в frontend | 2 protocol-fixture journeys passed (отдельно от реального loopback-теста) |
| Перезапуск backend | `PYTHONDONTWRITEBYTECODE=1 python3 scripts/demo/recovery.py` | RUNNING -> generation 2 -> FINISHED/ACCEPTED; один POST batch, номер попытки сохранён |
| Build contexts | `python3 scripts/delivery/context_check.py` | Все три прошли; demo fixture, `.delivery`, secrets/datasets/papers исключены |
| Workflow / shell | pinned actionlint, `bash -n scripts/demo/demo.sh` | passed |
| Локальные логи | поиск сгенерированных секретов, полного текста fixture-программ, JWT-паттерна без печати логов | В просмотренных backend/edge/fixture логах совпадений нет |

Browser-тест обнаружил реальную гонку logout: смена ключа router-view повторно монтировала защищённую страницу до завершения перехода на login, вызывая запрос профиля без JWT. `App.vue` теперь скрывает защищённый route при отсутствии токена; добавлен regression test. После пересборки образа browser journey проходит без 401 и ошибок STOMP/SockJS.

Recovery helper ограничен фиксированным локальным проектом и адресом; SQL читает только метаданные задания под несуперпользовательской ролью. Он дожидается долговременного RUNNING checkpoint, выполняет KILL и явный start собственного backend-контейнера, затем проверяет продолжение GET polling с сохранёнными tickets. Явный start нужен, поскольку операторский Docker kill может подавлять restart policy. Наблюдение: generation **1 -> 2**, worker attempt_count **1 -> 2**, финал ACCEPTED, единственный provider POST, номер формальной попытки не изменился. Это не проверка автоматического восстановления всего хоста, реального Judge0, backup/restore или отката версий.

Локальные артефакты (не коммитятся): `.delivery/demo/access.md`, `access.json`, `compose.env`, `workspace.png`, `recovery.json`. Временные журналы проверок: `/tmp/socrates-demo-{start,cycle,browser,regression-browser,unit,lint,frontend-build,context,recovery}.log`. Backend Java-код не менялся, полный `mvnw verify` повторно в этой итерации не запускался; результаты Step 8 не выдаются за новый прогон. Модели не переобучались, датасеты/метрики/статьи не менялись. Все созданные interaction records принадлежат синтетическому локальному демо и не являются данными исследования с реальными студентами.

## Зафиксированные локальные образы

Это локальные image IDs для трассируемости, **не** опубликованные GHCR production digests:

| Образ | Local ID |
| --- | --- |
| socratesai/backend:delivery-test | `sha256:40f7c98ab67cd544e9b0414ebf02e22cbfe75745fafd8533d5152cacc4c0f5ea` |
| socratesai/frontend:delivery-test | `sha256:8b950e0b46ddac6fbf6f21058e7c669b94206bf24fb300e164e481cf802c5535` |
| socratesai/policy-api:delivery-test | `sha256:6530dd3042c66eaf8a6547860259307c9c4f9ef4b83d7832411a0d5125e1e051` |

## Что остаётся до production

Пункты P9.1–P9.11 остаются открытыми, с записанным локальным подмножеством P9.6/P9.8; P9.12 закрыт документацией с проверенными локальными командами. Полная инструкция пользователя: [deploy/demo/README.md](../../deploy/demo/README.md); production prerequisites/runbook: [DEPLOYMENT.md](../../DEPLOYMENT.md).

Для продолжения нужны целевой хост и доступ, домен/DNS/TLS с renewal, сертифицированная отдельная песочница с фактическим runtime catalog, защищённые release-настройки и успешный CI/registry manifest. На момент проверки [baseline CI a156d89](https://github.com/koshikovme/socratesai/actions/runs/39718406876) завершился ошибкой в сборке backend-образа (`dependency:go-offline`); Java/frontend/policy/infrastructure jobs прошли, publish пропущен. Полные job logs без доступа не получены, причина Maven-сбоя не установлена. Локальное демо не означает исправление этого remote gate. Нового version-tag release не создавали.

Также открыты production backup/restore, alerts/retention, rehearsal отката совместимых образов и остальные failure-сценарии, проверка актуальных уязвимостей (в том числе PostgreSQL helper findings из Step 8), реальное HTTPS/WSS/judging acceptance и запись production URL/операционного владельца. Никакие базы 21/22, чужие стенды или платные ресурсы не затронуты. Стенд оставлен работающим локально.
