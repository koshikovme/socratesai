# Production Deployment

This repository is now deployable as three services:

- `backend`: Spring Boot API on port `8080`
- `policy-api`: FastAPI ML policy service on port `8001`
- `postgres`: PostgreSQL 16

## Required Environment

Create a `.env` file on the host:

```env
POSTGRES_DB=socratesdb
POSTGRES_USER=socrates
POSTGRES_PASSWORD=replace-with-strong-db-password
APP_JWT_SECRET=base64-encoded-secret-at-least-64-bytes-after-decoding
APP_WEBSOCKET_ALLOWED_ORIGINS=https://your-domain.example
APP_POLICY_MODE=ml
APP_POLICY_ML_ENABLED=true
APP_OPENAPI_ENABLED=false
APP_SWAGGER_ENABLED=false
APP_PORT=8080
```

Generate secrets outside the repo and never commit `.env`. `APP_JWT_SECRET` must be base64 because `JwtService` decodes it before constructing the HMAC key.

## Local Production Smoke Test

```powershell
docker compose -f docker-compose.prod.yml --env-file .env up --build -d
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8080/actuator/health
```

The backend runs Flyway migrations on startup, including the expert-label and outcome-label schema.

## Hosting Options

Use any host that can run Docker Compose, for example a VPS, Render private services, Railway, Fly.io machines, or a Kubernetes cluster. For a managed PaaS, split the compose file into:

- one PostgreSQL managed database,
- one Spring Boot web service,
- one FastAPI private service.

Set `APP_DATASOURCE_URL` to the managed database JDBC URL and `APP_POLICY_ML_BASE_URL` to the private URL of `policy-api`.

## Health Checks

- Backend: `GET /actuator/health`
- ML policy API: `GET /health`
- Expert agreement: `GET /api/interactions/expert-labels/agreement` with `TEACHER` or `ADMIN`
- Policy dataset export: `GET /api/interactions/policy-dataset`
- Expert labels export: `GET /api/interactions/expert-labels/export` with `TEACHER` or `ADMIN`

## What Still Requires Real Credentials

I cannot push this to a public host without the hosting account, project name, domain, and secrets. With those available, the deployment path is now mechanical: build images, set the environment variables above, attach PostgreSQL, and start the services.
