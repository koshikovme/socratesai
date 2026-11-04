# SocratesAI

SocratesAI is a full-stack teaching assistant for programming practice. The repository contains:

1) A Spring Boot backend for auth, tasks, analysis, mentor feedback, and WebSocket updates 
2) A Vue 3 + Vite frontend
3) An optional FastAPI-based ML policy service used by the mentor feedback flow

## Live installation

**https://socratesai.duckdns.org** hosts the native catalog, teacher authoring, Monaco workspace,
authenticated mentor, sample runs and formal submissions. Python 3.12, Java 21 and C++17
compile and execute on a separate isolated Judge0/Isolate VM. Accepted submissions update
saved progress. Python mentor inference can use ML and bounded Gemini wording; Java/C++ use rules.

The repository owner operates the installation. Owner account credentials are available only
in the protected local `.delivery/production-access.md` file. Public signup grants STUDENT;
staff roles require audited administrator provisioning.
See the [production evidence](tasks/socrates-evolution/result-9-production.md) and
[deployment/incident runbook](DEPLOYMENT.md) for release, backups, limits and commands.
All recorded acceptance/load checks use synthetic accounts and do not establish classroom outcomes.

## Repository Layout

`src/`: Spring Boot backend

`socratesai-frontend/`: Vue frontend

`ml/`: Python ML policy service

`docker-compose.yml`: local PostgreSQL for development

## Локальная демонстрация

Для знакомства с приложением без хостинга запустите из корня репозитория (Docker Compose 2.24.4+, Bash, Python 3):

```bash
bash scripts/demo/demo.sh up
```

Откройте **http://localhost:8088**. Учебные аккаунты и примеры решений находятся в локальном файле `.delivery/demo/access.md` (секреты исключены из Git). Работают native-каталог, преподавательские экраны, Monaco, WS-ментор, очередь Run/Submit и прогресс. **Исполнение решений имитируется; студенческие программы не запускаются.** Подробный сценарий и команды остановки: [локальное демо](deploy/demo/README.md).

## Prerequisites

1) Java 21

2) Node.js 22.23.1 and npm

3) Docker Desktop or Docker Engine

4) Python 3.12 for the packaged ML service (research scripts require Python 3.11+)

## Default Local Ports

Backend: `8080`

Frontend: `5173`

PostgreSQL: `5433`

ML policy service: `8001`

## Quick Start

### 1. Start PostgreSQL

From the repository root path:

```bash
docker compose up -d pg_db
```

This starts PostgreSQL with these default credentials:

- Database: `socratesdb`
- Username: `postgres`
- Password: `postgres`

### 2. Run the backend

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

### 4. Run the frontend


```bash
cd socratesai-frontend
npm install
npm run dev
```

The frontend runs at:

```text
http://localhost:5173
```

### 5. Run the optional ML policy service

If you want the backend to use the Python model-based policy selector instead of rule mode:

```bash
cd ml
python -m venv .venv
```

Windows PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn policy_api:app --host 0.0.0.0 --port 8001
```

macOS/Linux:

```bash
source .venv/bin/activate
pip install -r requirements.txt
uvicorn policy_api:app --host 0.0.0.0 --port 8001
```

Health check:

```text
http://localhost:8001/health
```

## Useful Commands

Backend tests:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

Frontend tests:

```bash
cd socratesai-frontend
npm test
```

Frontend production build:

```bash
cd socratesai-frontend
npm run build
```

## Local Development Checklist

1. Start PostgreSQL with Docker Compose.
2. Run the Spring Boot backend on `8080`.
3. Run the Vue frontend on `5173`.
4. Optionally run the ML policy service on `8001`, or switch the backend to `APP_POLICY_MODE=RULE`.
5. Open the frontend and register a user.

## Production containers and delivery

See [DEPLOYMENT.md](DEPLOYMENT.md) for the live four-service application deployment, separate execution VM, external secrets/TLS, private networking, release-tag GHCR delivery, encrypted backups and recovery commands. Application traffic uses edge ports 80/443; the development ports above are not production mappings. A main push validates; a new reviewed release tag publishes and promotes the tested application digests. Executor upgrades require separate live isolation verification.
