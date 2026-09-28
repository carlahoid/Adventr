#!/bin/sh
# Runs once, when the Postgres data volume is empty: creates separate databases and
# owners for the app and for Keycloak.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
	-v app_password="$APP_DB_PASSWORD" \
	-v keycloak_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE adventr LOGIN PASSWORD :'app_password';
CREATE DATABASE adventr OWNER adventr;
REVOKE ALL ON DATABASE adventr FROM PUBLIC;

CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
