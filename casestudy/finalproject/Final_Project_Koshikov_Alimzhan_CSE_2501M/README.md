# SocratesAI

SocratesAI is a full-stack teaching assistant for programming practice. The repository contains:

1) A Spring Boot backend for auth, tasks, analysis, mentor feedback, and WebSocket updates 
2) A Vue 3 + Vite frontend
3) An optional FastAPI-based ML policy service used by the mentor feedback flow

## Repository Layout

`src/`: Spring Boot backend

`socratesai-frontend/`: Vue frontend

`ml/`: Python ML policy service

`docker-compose.yml`: local PostgreSQL for development

## Prerequisites

1) Java 21

2) Node.js 18+ and npm 

3) Docker Desktop or Docker Engine

4) Python 3.11+ for the optional ML service

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
