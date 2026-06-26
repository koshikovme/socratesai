# Как презентовать SocratesAI, ML и эксперименты руководителю

Этот документ нужен как шпаргалка для встречи с руководителем. Он кратко восстанавливает, что было сделано в проекте, какие ML-результаты есть, какие эксперименты запускались, какие цифры можно уверенно говорить, а где нужно честно обозначить ограничения.

## 1. Короткий pitch на 30-40 секунд

Мой проект SocratesAI - это real-time programming mentor для студентов CS1, то есть для начинающих Java/programming студентов. Главная идея не в том, что "AI объясняет код", а в том, что система сначала выбирает педагогическое действие: подсветить подозрительный участок, дать conceptual hint, задать guiding question или вообще не вмешиваться. Только после этого Gemini или template формулирует короткий feedback.

Я проверил систему не только как приложение, но и как исследовательский pipeline:

- backend работает через реальный HTTP endpoint и PostgreSQL;
- есть analyzer, session state, rule policy и ML policy;
- собран problem-suite benchmark на 12 задачах;
- расширен ML dataset до 381,003 строк;
- обучены и сравнены несколько моделей;
- проведен ablation study;
- проведен fixed ML + Gemini benchmark: 360 events, 0 runtime errors, 95.83% agreement и 95.80% macro F1.

Важно: я не утверждаю, что уже доказал learning gains. Я утверждаю, что доказал техническую feasibility и action-selection validity. Для learning gains нужен classroom pilot.

## 2. Какую проблему решает проект

В CS1 студенты часто застревают не на больших архитектурных вопросах, а на маленьких ошибках:

- missing statement ending;
- wrong loop boundary;
- off-by-one;
- wrong condition;
- possible null access;
- unfinished implementation;
- repeated conceptual error.

Обычные automated assessment tools говорят, что тест упал или код не компилируется. Open-ended LLM assistant может дать слишком много, иногда почти готовое решение. Мой подход находится между ними:

1. Сначала analyzer извлекает structured signals.
2. Потом session state учитывает историю студента.
3. Потом policy выбирает тип вмешательства.
4. Только потом feedback generator формулирует текст.

Главная research idea: **feedback generation should be controlled by an explicit pedagogical policy, not by an unrestricted LLM.**

## 3. Архитектура проекта

Код backend находится здесь:

`src/main/java/com/masters/socratesai`

Основные модули:

| Module | Что делает |
|---|---|
| `analyzer` | Анализирует код, определяет error type, compile status, suspicious region, tests passed/failed |
| `mentor` | Главный mentoring workflow: analyzer -> context -> policy -> feedback |
| `mentor/policy` | Rule-based policy, ML policy selector, guardrails |
| `mentor/feedback` | Template feedback, Gemini feedback, OpenAI experimental provider |
| `interaction` | Логи mentor interactions, expert labels, outcome labels |
| `session` | Student-task session state |
| `task` | Programming tasks |
| `auth`, `security`, `user` | JWT auth, users, roles |
| `websocket` | Realtime feedback path |

### Самая важная архитектурная мысль

SocratesAI не дает LLM самому решать, что делать. LLM используется только после policy decision. Это сильнее для статьи, потому что:

- поведение системы auditable;
- можно сравнивать rule policy, ML policy и no-policy baseline;
- можно ограничить direct solution disclosure;
- можно анализировать action-level quality независимо от wording quality.

## 4. Какие feedback actions есть

| Action | Meaning | Когда использовать |
|---|---|---|
| `CODE_HIGHLIGHT` | Подсветить место в коде | Когда важнее показать suspicious region, чем объяснять концепт |
| `CONCEPTUAL_HINT` | Короткий conceptual hint | Когда студенту нужен намек на идею |
| `GUIDING_QUESTION` | Вопрос, который заставляет проверить код | Когда лучше не давать прямую подсказку |
| `NO_FEEDBACK` | Не вмешиваться | Когда код локально нормальный или evidence weak |

Это важно объяснить руководителю: модель не просто предсказывает "правильный/неправильный". Она выбирает **тип педагогической помощи**.

## 5. Что было сделано в ML

Папка:

`ml`

### 5.1 Dataset 1: problem-suite rubric dataset

Файл:

`ml/problem_suite_policy_dataset.csv`

Размер:

- 1,080 rows;
- 12 programming problems;
- target column: `target_feedback_action`;
- group holdout by `problem_slug`.

Зачем нужен:

Это главный dataset для action-selection validity, потому что target label отражает rubric action для code state. Его можно объяснять как controlled benchmark dataset.

Результат обучения:

Файл отчета:

`ml/problem_suite_policy_model_report.md`

Ключевые цифры:

| Metric | Value |
|---|---:|
| Dataset rows | 1,080 |
| Train rows | 810 |
| Test rows | 270 |
| Split | group holdout by `problem_slug` |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

Как объяснять:

Этот результат показывает, что rubric labels learnable from runtime features. Но это не proof of learning gains. Это proof, что policy action можно предсказывать из analyzer/session features.

### 5.2 Dataset 2: expanded weak-label dataset

Файл:

`ml/policy_dataset_expanded_full.csv`

Размер:

- 381,003 rows;
- 117,232 coding sessions;
- target column: `feedback_action`;
- full corpus expansion from local training data.

Важно:

Я пытался расширить датасет примерно до 10x, но без искусственного дублирования максимум осмысленно вышел около 8.9x относительно старого expanded dataset. Это лучше, чем просто дублировать строки, потому что использованы реальные unique solution sessions из корпуса.

Распределение labels:

| Label | Count |
|---|---:|
| `CODE_HIGHLIGHT` | 87,914 |
| `CONCEPTUAL_HINT` | 117,232 |
| `GUIDING_QUESTION` | 58,625 |
| `NO_FEEDBACK` | 117,232 |

Зачем нужен:

Это weak-label dataset. Он нужен для scale testing, model comparison и ablation. Его нельзя называть expert-labelled classroom dataset.

### 5.3 Final trained model on expanded dataset

Файлы:

- `ml/mentor_policy_model_expanded_full.joblib`
- `ml/mentor_policy_model_expanded_full_metadata.json`
- `ml/mentor_policy_model_expanded_full_metrics.json`
- `ml/mentor_policy_model_expanded_full_report.md`
- `ml/mentor_policy_model_expanded_full_confusion_matrix.png`

Результат:

| Metric | Value |
|---|---:|
| Dataset rows | 381,003 |
| Train rows | 303,515 |
| Test rows | 77,488 |
| Split | group holdout by `problem_id` |
| Accuracy | 1.0000 |
| Macro F1 | 1.0000 |

Как объяснять 1.0000, чтобы это не выглядело подозрительно:

Нужно сказать так:

> This result is expected because this is a weak-label policy-imitation dataset. It validates that exported runtime features can reproduce the current deployable policy at scale. I do not present it as evidence of student learning.

То есть это не "магически идеальная педагогическая модель". Это проверка, что ML pipeline стабильно воспроизводит policy behavior на большом exported dataset.

## 6. Model comparison и ablation study

Файлы:

- `ml/policy_model_study_report.md`
- `ml/policy_model_study_results.json`
- `ml/policy_model_study_expanded_full_report.md`
- `ml/policy_model_study_expanded_full_results.json`

Какие модели сравнивались:

- Rule baseline;
- Logistic Regression;
- Random Forest;
- XGBoost;
- LightGBM;
- small neural classifier.

Какие ablations были:

- all features;
- without history features;
- without analyzer features;
- without suspicious region;
- without last feedback action.

Главный вывод:

На expanded full dataset почти все модели с all features воспроизводят policy на 1.0000 accuracy / 1.0000 macro F1. Но если убрать analyzer features, результат падает:

| Feature set | Accuracy | Macro F1 |
|---|---:|---:|
| All features | 1.0000 | 1.0000 |
| Without analyzer features | 0.9232 | 0.8813 |

Как это презентовать:

> The ablation study shows that analyzer-derived features carry real signal. This supports the architecture: the mentor is not only an LLM wrapper. The structured code analyzer is an important part of policy prediction.

Это хороший ответ на критику "novelty is mainly integration". Здесь есть технический аргумент: structured analyzer/session features measurably affect policy prediction.

## 7. Experiments

Папка:

`experiments`

### 7.1 Real HTTP PostgreSQL replay

Это проверка, что backend mentoring loop работает быстро через настоящий HTTP endpoint и PostgreSQL, а не только как unit test.

Результат:

| Metric | Value |
|---|---:|
| Events | 570 |
| Mean latency | 44.11 ms |
| Median latency | 46 ms |
| P95 latency | 58 ms |
| P99 latency | 68 ms |
| Throughput | 22.66 req/s |

Как объяснять:

Это показывает, что core mentoring loop быстрый, когда feedback идет через deterministic templates/rule path. Это backend feasibility result.

### 7.2 Concurrent stress smoke test

Результат:

| Metric | Value |
|---|---:|
| Requests | 400 |
| Concurrency | 8 |
| Errors | 0 |
| Mean latency | 37.59 ms |
| P95 latency | 56 ms |
| P99 latency | 61 ms |
| Throughput | 211.19 req/s |

Как объяснять:

Это не production load test на университетский масштаб. Это smoke test, который показывает, что pipeline не ломается под modest parallel load.

### 7.3 Baseline policy comparison

Файл:

`experiments/results/problem-suite-comparison/summary.json`

Контекст:

Это deterministic 12-problem comparison, где есть rule policy, ML-labelled mode и no-policy baseline.

Ключевой baseline:

| Mode | Events | Agreement | Macro F1 |
|---|---:|---:|---:|
| Rule policy | 288 | 87.50% | 85.90% |
| Fixed no-policy baseline | 288 | 38.89% | 14.00% |

Как использовать:

Главное здесь - не старый ML result, а no-policy baseline. Он показывает, что generic hint-only behavior слабый, потому что он не умеет:

- подсвечивать код;
- задавать guiding question;
- молчать в `NO_FEEDBACK` случаях.

### 7.4 Valid fixed ML + Gemini benchmark

Самый важный runtime benchmark:

`experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml/summary.md`

Это главный результат, который надо показывать руководителю.

Условия:

- Docker Compose backend;
- PostgreSQL;
- fixed ML policy API contract;
- fresh student ids;
- Gemini 2.5 Flash-Lite;
- captured feedback text and feedback source;
- 12 programming problems;
- 5 cohorts;
- 360 events.

Overall:

| Metric | Value |
|---|---:|
| Events | 360 |
| Successful | 360 |
| Errors | 0 |
| Agreement | 95.83% |
| Macro F1 | 95.80% |
| Mean latency | 1079.21 ms |
| P95 latency | 2072 ms |
| Throughput | 0.93 req/s |

Action distribution:

| Action | Expected | Actual |
|---|---:|---:|
| `CODE_HIGHLIGHT` | 100 | 105 |
| `CONCEPTUAL_HINT` | 140 | 145 |
| `GUIDING_QUESTION` | 60 | 60 |
| `NO_FEEDBACK` | 60 | 50 |

Per-class metrics:

| Action | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `CODE_HIGHLIGHT` | 95.24% | 100.00% | 97.56% | 100 |
| `CONCEPTUAL_HINT` | 93.10% | 96.43% | 94.74% | 140 |
| `GUIDING_QUESTION` | 100.00% | 100.00% | 100.00% | 60 |
| `NO_FEEDBACK` | 100.00% | 83.33% | 90.91% | 60 |

Problem coverage:

| Problem | Agreement |
|---|---:|
| `binary-search` | 100.00% |
| `climbing-stairs` | 100.00% |
| `longest-substring` | 100.00% |
| `matrix-diagonal-sum` | 100.00% |
| `merge-sorted-array` | 100.00% |
| `palindrome` | 100.00% |
| `reverse-linked-list` | 100.00% |
| `roman-to-integer` | 100.00% |
| `trapping-rain-water` | 100.00% |
| `two-sum` | 100.00% |
| `valid-parentheses` | 83.33% |
| `lru-cache` | 66.67% |

Remaining weak spots:

- `lru-cache`, first conceptual error;
- `lru-cache`, local completion;
- `valid-parentheses`, local completion.

Как объяснять:

> The strongest current result is the fixed ML+Gemini benchmark. It tests the full system, not only offline ML. The backend sends analyzer/session features to the ML policy API, the policy selects the feedback action, and Gemini realizes the message. The system reached 95.83% agreement with target feedback actions with no runtime errors.

## 8. Важный баг, который был найден и исправлен

Это важно рассказать руководителю, потому что показывает engineering rigor.

Проблема:

Backend отправлял features в ML policy API в camelCase JSON:

- `compileSuccess`;
- `testsPassed`;
- `sameErrorCount`.

А FastAPI policy service ожидал snake_case:

- `compile_success`;
- `tests_passed`;
- `same_error_count`.

Из-за этого старые ML runtime runs могли падать в fallback на rule policy.

Что исправлено:

- `src/main/java/com/masters/socratesai/mentor/policy/MlPolicySelector.java`
  - теперь отправляет snake_case payload;
- `src/test/java/com/masters/socratesai/mentor/policy/MentorPolicyServiceTest.java`
  - добавлен regression test на snake_case payload;
- `src/main/java/com/masters/socratesai/mentor/dto/MentorResponse.java`
  - добавлен `feedbackSource`;
- `src/main/java/com/masters/socratesai/mentor/service/MentorService.java`
  - response теперь возвращает source feedback;
- `experiments/problem_suite_http_benchmark.py`
  - benchmark теперь сохраняет `feedback_source` и `feedback_text`.

Как объяснять:

> I discovered that the previous ML runtime benchmark was methodologically unsafe because ML API fallback could hide the issue. I fixed the backend-to-policy API contract and reran the benchmark with fresh student ids. That fixed run is the one I use as the primary result.

Что нельзя делать:

Не надо показывать старый 720-event Gemini ML result как primary ML result. Он был до фикса API contract и может отражать fallback behavior. Primary result сейчас:

`experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml`

## 9. Figures, которые надо показать

Файлы:

- `ijep/images/sist_latency_timeline.png`
- `ijep/images/sist_per_class_metrics.png`
- `ijep/images/sist_problem_agreement.png`
- `ijep/images/sist_ablation_analyzer_features.png`

Как показывать:

1. **Latency timeline**
   - показывает runtime latency в ML+Gemini benchmark;
   - mean около 1.08 sec, P95 около 2.07 sec;
   - это нормально для LLM-enabled feedback.

2. **Per-class metrics**
   - показывает, что `GUIDING_QUESTION` идеально распознается;
   - `NO_FEEDBACK` слабее из-за local-completion cases.

3. **Problem agreement**
   - показывает, что 10 из 12 задач имеют 100% agreement;
   - слабые задачи: `lru-cache`, `valid-parentheses`.

4. **Ablation analyzer features**
   - самый важный технический chart;
   - показывает, что без analyzer features macro F1 падает до 0.8813;
   - значит analyzer реально нужен.

## 10. Что было обновлено в статье

Файлы:

- `ijep/sist2026_paper.tex`
- `ijep/sist2026_paper.pdf`
- `ijep/mycurrentpaper.tex`
- `ijep/mycurrentpaper.pdf`

IEEE SIST version:

- `ijep/sist2026_paper.tex`;
- compiled PDF: `ijep/sist2026_paper.pdf`;
- 5 pages in IEEEtran format.

Главные обновления:

- добавлены новые ML+Gemini results;
- добавлен expanded 381,003-row dataset;
- добавлен ablation study;
- добавлены charts;
- novelty сформулирована как policy-guided mentoring, not generic LLM assistant;
- limitations сделаны честными: нет learning-gain claim без classroom pilot.

## 11. Как построить презентацию на встрече

### Вариант на 10 минут

#### Slide 1 - Problem

Сказать:

> In CS1, students often get stuck on small mistakes during coding. A compiler tells them that something failed, but not what level of pedagogical help is appropriate. A general LLM can help, but it may reveal too much. My project solves this by choosing a bounded feedback action first.

Показать:

- 4 actions: highlight, hint, question, no feedback.

#### Slide 2 - Architecture

Показать:

- `ijep/images/arch-SIST.png`

Сказать:

> The key design decision is separation. Analyzer and student state come before policy. Gemini comes only after policy. So the LLM realizes a selected action, but does not control the mentoring strategy.

#### Slide 3 - ML task

Сказать:

> The ML task is not "generate feedback text". The ML task is multi-class classification of the pedagogical action.

Показать labels:

- `CODE_HIGHLIGHT`;
- `CONCEPTUAL_HINT`;
- `GUIDING_QUESTION`;
- `NO_FEEDBACK`.

#### Slide 4 - Datasets

Показать:

| Dataset | Size | Purpose |
|---|---:|---|
| Problem-suite rubric dataset | 1,080 rows | target-action benchmark |
| Expanded weak-label dataset | 381,003 rows | scale, model comparison, ablation |
| Fixed ML+Gemini runtime benchmark | 360 events | end-to-end validation |

Сказать:

> The expanded dataset is weak-labelled, so I use it for pipeline and ablation evidence. The problem-suite dataset is more important for rubric-based action validity.

#### Slide 5 - Model comparison and ablation

Показать:

- `ijep/images/sist_ablation_analyzer_features.png`

Сказать:

> Several models can reproduce the policy with all features. The important result is the ablation: when analyzer features are removed, macro F1 drops to 0.8813. This supports the technical contribution of the analyzer-policy architecture.

#### Slide 6 - Runtime ML+Gemini benchmark

Показать:

| Metric | Value |
|---|---:|
| Events | 360 |
| Errors | 0 |
| Agreement | 95.83% |
| Macro F1 | 95.80% |
| Mean latency | 1079.21 ms |
| P95 latency | 2072 ms |

Сказать:

> This is the primary result because it tests the real backend, PostgreSQL session state, ML policy API, and Gemini feedback generation together.

#### Slide 7 - Per-class and per-problem quality

Показать:

- `ijep/images/sist_per_class_metrics.png`
- `ijep/images/sist_problem_agreement.png`

Сказать:

> The strongest class is guiding question. The weakest remaining area is no-feedback for local-completion states, especially in LRU cache and valid parentheses. This is a clear next improvement target.

#### Slide 8 - What is honest claim?

Сказать:

> I can claim technical feasibility and action-selection validity. I cannot yet claim improved learning outcomes. For that I need a classroom pilot with pre-test, post-test, and outcome labels.

#### Slide 9 - Paper status

Показать:

- `ijep/sist2026_paper.pdf`

Сказать:

> I updated the paper to address the previous review criticism: clearer novelty, larger dataset description, benchmark comparison, technical ablation, and explicit limitations.

## 12. Что сказать, если руководитель спросит "В чем новизна?"

Ответ:

> The novelty is not that I use Gemini. The novelty is the controlled mentoring pipeline. The system separates analyzer signals, student state, pedagogical action selection, and message realization. This allows us to audit and evaluate the mentoring decision before any LLM text is produced.

Дополнить:

- LLM assistants usually focus on response generation.
- My system focuses on deciding **what type of feedback** should happen.
- This is safer for CS1 because it can choose `NO_FEEDBACK` or `GUIDING_QUESTION` instead of always explaining.

## 13. Что сказать, если спросят "Почему ML result 1.0?"

Ответ:

> The 1.0 result is on weak-label policy imitation and rubric target-action splits. I do not present it as proof that the model is pedagogically perfect. It shows that the exported features are sufficient to reproduce the current policy/rubric mapping. The more realistic runtime result is the fixed ML+Gemini benchmark: 95.83% agreement and 95.80% macro F1.

Важно:

Не защищать 1.0 как "модель идеально умная". Защищать как:

- pipeline validation;
- feature sufficiency;
- policy imitation;
- controlled labels.

## 14. Что сказать, если спросят "Где реальные студенты?"

Ответ:

> Current evaluation is controlled and event-level. It tests whether the system works, whether the policy matches target labels, and whether the ML pipeline is technically sound. The next step is a classroom pilot with 10-20 students, pre-test, practice, post-test, and helpfulness survey.

План:

1. Собрать 200-500 manually reviewed events.
2. Два эксперта размечают 50-100 overlapping events.
3. Посчитать Cohen's kappa.
4. Логировать outcomes:
   - helpful/not helpful;
   - fixed after feedback;
   - time to fix;
   - repeated same error.

## 15. Что сказать, если спросят "Почему Gemini, а не только templates?"

Ответ:

> Templates are stable and cheap, but they are rigid. Gemini gives more natural feedback wording. However, Gemini does not control the pedagogical decision. The policy chooses the action first, and Gemini only realizes that action in short text. If Gemini fails, the system falls back to templates.

В benchmark:

- Gemini responses: 317;
- template fallbacks: 43.

## 16. Что сказать, если спросят "Какие слабые места?"

Честный список:

1. `NO_FEEDBACK` still needs improvement.
   - Some local-completion states still receive hints.
   - Weak problems: `lru-cache`, `valid-parentheses`.

2. Expanded dataset is weak-labelled.
   - Good for scale and ablation.
   - Not a substitute for instructor-labelled classroom data.

3. No learning-gain proof yet.
   - Need classroom study.

4. Gemini fallback reasons are not separated enough.
   - We know source is `gemini` or `template`.
   - We should log whether fallback happened due to timeout, provider error, empty response, etc.

## 17. Что лучше НЕ говорить

Не говорить:

- "Я доказал, что студенты лучше учатся."
- "Модель идеальная, потому что accuracy 1.0."
- "Gemini сам выбирает правильную подсказку."
- "Dataset expert-labelled."
- "Это production-ready для всего университета."

Говорить вместо этого:

- "I validated technical feasibility and policy-action selection."
- "The 1.0 score is policy imitation / rubric mapping validation."
- "Gemini realizes the selected action, not the strategy."
- "The expanded dataset is weak-labelled."
- "The next step is classroom evaluation."

## 18. Какие файлы открыть на встрече

### Paper

- `ijep/sist2026_paper.pdf`
- `ijep/sist2026_paper.tex`

### Figures

- `ijep/images/sist_latency_timeline.png`
- `ijep/images/sist_per_class_metrics.png`
- `ijep/images/sist_problem_agreement.png`
- `ijep/images/sist_ablation_analyzer_features.png`

### ML reports

- `ml/mentor_policy_model_expanded_full_report.md`
- `ml/problem_suite_policy_model_report.md`
- `ml/policy_model_study_expanded_full_report.md`
- `ml/policy_model_study_report.md`

### Main benchmark

- `experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml/summary.md`
- `experiments/results/problem-suite-gemini-quality-fixed-fresh-5c/ml/events.csv`

### Code modules

- `src/main/java/com/masters/socratesai/analyzer`
- `src/main/java/com/masters/socratesai/mentor`
- `src/main/java/com/masters/socratesai/mentor/policy`
- `src/main/java/com/masters/socratesai/mentor/feedback`
- `src/main/java/com/masters/socratesai/interaction`

## 19. Демо-план

Если нужно показать live demo:

1. Поднять backend stack:

```powershell
$env:APP_PORT='18080'
docker compose -f docker-compose.prod.yml up -d
```

2. Проверить health:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:18080/actuator/health
```

3. Показать flow:

- login/register;
- open task;
- write code with small mistake;
- ask mentor;
- show selected action;
- show feedback text;
- show interaction logging.

4. Объяснить:

> The output is not just text. Each response has an action, source, analyzer result, and log entry. That is what makes it researchable.

## 20. Как связать с IEEE SIST paper

Сказать:

> After the previous review, I updated the paper around the weak points. The previous feedback said novelty and dataset scale were not clear enough, and that technical depth was limited. The new version addresses this by adding a larger dataset, model comparison, ablation study, benchmark details, and clearer positioning against generic LLM assistants.

Что улучшено относительно rejected version:

| Reviewer concern | Что теперь есть |
|---|---|
| Novelty unclear | Policy-guided action selection before LLM realization |
| Dataset description weak | 1,080-row rubric dataset and 381,003-row expanded dataset clearly described |
| No benchmark comparison | No-policy baseline and fixed ML+Gemini benchmark |
| Low technical depth | Model comparison and feature ablation |
| Overclaiming risk | Explicit limitation: no learning-gain claim yet |

## 21. Final version of the claim

Самая безопасная и сильная формулировка:

> SocratesAI demonstrates that a policy-guided programming mentor can select bounded pedagogical feedback actions with high agreement against rubric labels in a controlled CS1 problem-suite benchmark, while preserving an auditable separation between code analysis, student state, action selection, and LLM-based feedback realization.

Коротко:

> It is not just an AI chatbot. It is an auditable policy-guided mentoring pipeline.

