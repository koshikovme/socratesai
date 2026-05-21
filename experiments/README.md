# SocratesAI Experiments

This directory stores reproducible technical evaluation artifacts for the paper draft.

## Mentor Pilot Replay

Runs 570 scripted mentor interactions through the authenticated Spring Boot request pipeline and then runs a concurrent smoke test.

Windows PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dtest=MentorPilotReplayExperimentTest" "-Dsocratesai.experiments=true" test
```

Outputs:

- `experiments/results/mentor-pilot-replay/events.csv`
- `experiments/results/mentor-pilot-replay/summary.md`

Interpretation boundary: these are operational prototype results, not classroom learning-gain results.

## Real HTTP PostgreSQL Benchmark

Runs the same scripted mentor interactions through a real Spring Boot HTTP process backed by PostgreSQL.

Prerequisites:

- PostgreSQL is available on `localhost:5433` with database `socratesdb`, user `postgres`, and password `postgres` (for example through `docker compose up -d pg_db`).
- The backend jar has been rebuilt with `.\mvnw.cmd -DskipTests package`.
- The backend is running on `localhost:18080`.

Example replay:

```powershell
python experiments\real_http_benchmark.py `
  --base-url http://localhost:18080 `
  --mode RULE `
  --output-dir experiments\results\real-http-postgres\rule-full `
  --events 570 `
  --stress-requests 400 `
  --stress-concurrency 8 `
  --environment "Real HTTP Spring Boot process, PostgreSQL Docker Compose database"
```

Outputs:

- `experiments/results/real-http-postgres/rule-full/events.csv`
- `experiments/results/real-http-postgres/rule-full/stress.csv`
- `experiments/results/real-http-postgres/rule-full/summary.md`

Interpretation boundary: this benchmark measures local real-HTTP prototype behavior with PostgreSQL. It is not a classroom learning-outcome study.

## Policy Comparison and Labels

Generate 100 rubric-coded labels from the rule replay, compare policy modes, and create the paper figure.

```powershell
python experiments\generate_expert_labels.py `
  --events-csv experiments\results\real-http-postgres\rule-100\events.csv `
  --output experiments\labels\expert_labels_100.csv `
  --limit 100

python experiments\compare_policy_modes.py `
  --rule-events experiments\results\real-http-postgres\rule-100\events.csv `
  --ml-events experiments\results\real-http-postgres\ml-100\events.csv `
  --no-policy-events experiments\results\real-http-postgres\no-policy-100\events.csv `
  --labels experiments\labels\expert_labels_100.csv `
  --output-dir experiments\results\policy-comparison `
  --limit 100

python experiments\generate_figures.py `
  --events-csv experiments\results\real-http-postgres\rule-full\events.csv `
  --output-prefix ijep\images\latency_action_figure
```

Outputs:

- `experiments/labels/expert_labels_100.csv`
- `experiments/results/policy-comparison/summary.md`
- `experiments/results/figures/latency_action_figure.png`
- `ijep/images/latency_action_figure.png`

Interpretation boundary: the 100 labels are rubric-coded labels for reproducible comparison. They should be confirmed by independent instructor labels before making strong pedagogical claims.

## Programming Problem-Suite Benchmark

Runs a deeper controlled benchmark over 12 programming problems, including easy and medium CS tasks plus harder algorithmic cases such as Trapping Rain Water and LRU Cache. Each problem is replayed as a short sequence of code states: syntax issue, unfinished work, first conceptual mistake, repeated mistake, partial compiling logic, and local completion.

Example:

```powershell
python experiments\problem_suite_http_benchmark.py `
  --base-url http://localhost:18080 `
  --mode RULE `
  --output-dir experiments\results\problem-suite\rule `
  --cohorts 4 `
  --environment "Real HTTP Spring Boot process, PostgreSQL Docker Compose database, 12-problem programming suite"
```

Run the same command for `ML` and `NO_POLICY`, then compare:

```powershell
python experiments\compare_problem_suite_modes.py `
  --rule-events experiments\results\problem-suite\rule\events.csv `
  --ml-events experiments\results\problem-suite\ml\events.csv `
  --no-policy-events experiments\results\problem-suite\no-policy\events.csv `
  --output-dir experiments\results\problem-suite-comparison

python experiments\generate_problem_suite_figure.py `
  --summary-json experiments\results\problem-suite-comparison\summary.json `
  --output-prefix ijep\images\problem_suite_policy_comparison
```

Outputs:

- `experiments/results/problem-suite/rule/summary.md`
- `experiments/results/problem-suite/ml/summary.md`
- `experiments/results/problem-suite/no-policy/summary.md`
- `experiments/results/problem-suite-comparison/summary.md`
- `ijep/images/problem_suite_policy_comparison.png`

Interpretation boundary: this is a controlled programming problem-suite evaluation with review-rubric action labels. It is stronger than a narrow smoke test, but it should be paired with real classroom outcome data before making learning-gain claims.

## Policy Model Metrics

Trains the weak-label Random Forest policy model and exports metrics.

```powershell
cd ml
python train_policy_model.py
```

Outputs:

- `ml/mentor_policy_model.joblib`
- `ml/mentor_policy_model_metadata.json`
- `ml/mentor_policy_model_metrics.json`
- `ml/mentor_policy_model_report.md`

Interpretation boundary: the dataset is weakly supervised; perfect imitation metrics show rule-label consistency, not pedagogical superiority.
