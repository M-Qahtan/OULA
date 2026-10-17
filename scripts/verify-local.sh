#!/usr/bin/env bash
# Dedicated, disposable local test stack; never uses the user's demo database volume.
set -euo pipefail
cd "$(dirname "$0")/.."
for dependency in docker java mvn node npm curl; do
  command -v "$dependency" >/dev/null || { echo "Missing prerequisite: $dependency" >&2; exit 1; }
done
if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  echo "Stop the local OULA API before starting isolated verification on port 8080." >&2
  exit 1
fi
export OULA_LOCAL_DB_PORT=55432
project="oula-verification"
api_pid=""
cleanup() {
  [[ -z "$api_pid" ]] || kill "$api_pid" 2>/dev/null || true
  docker compose -p "$project" -f compose.local.yml down --volumes >/dev/null
}
trap cleanup EXIT INT TERM
# This fixed project name is reserved exclusively for disposable verification data.
docker compose -p "$project" -f compose.local.yml down --volumes >/dev/null
docker compose -p "$project" -f compose.local.yml up -d --wait
export DB_URL="jdbc:postgresql://127.0.0.1:$OULA_LOCAL_DB_PORT/oula"
export DB_USER=oula DB_PASSWORD=oula_dev_only CI_DB_TEST=true
SPRING_PROFILES_ACTIVE= mvn -B -ntp verify
export SPRING_PROFILES_ACTIVE=dev-auth SERVER_ADDRESS=127.0.0.1
export OULA_CORS_ALLOWED_ORIGINS=http://127.0.0.1:3000
java -jar target/oula-0.3.0-SNAPSHOT.jar > /tmp/oula-verification-api.log 2>&1 &
api_pid=$!
ready=false
for i in {1..60}; do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then ready=true; break; fi
  kill -0 "$api_pid" 2>/dev/null || { cat /tmp/oula-verification-api.log; exit 1; }
  sleep 1
done
[[ "$ready" == true ]] || { cat /tmp/oula-verification-api.log; exit 1; }
cd apps/web
npm ci --no-audit --no-fund
npm run typecheck
npm test
export NEXT_TELEMETRY_DISABLED=1 NEXT_PUBLIC_OULA_DEV_AUTH=true
export NEXT_PUBLIC_OULA_API_BASE_URL=http://127.0.0.1:8080
npm run build
npx playwright install chromium
npm run test:integration
OULA_LOCAL_DEV_AUTH=false npm run test:e2e
