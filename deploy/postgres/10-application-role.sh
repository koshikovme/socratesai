#!/bin/sh
set -eu
[ "$APP_DATABASE_USERNAME" != "$POSTGRES_USER" ] || { echo "Application and operator database roles must differ" >&2; exit 1; }
# Runs only during a NEW volume's official PostgreSQL initialization.
# psql reads secrets from the environment, never command-line arguments or SQL logs.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
\getenv application_user APP_DATABASE_USERNAME
\getenv application_password APP_DATABASE_PASSWORD
\getenv application_database POSTGRES_DB
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'application_user', :'application_password') \gexec
ALTER DATABASE :"application_database" OWNER TO :"application_user";
ALTER SCHEMA public OWNER TO :"application_user";
REVOKE ALL ON DATABASE :"application_database" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
