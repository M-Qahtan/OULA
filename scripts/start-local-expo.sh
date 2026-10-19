#!/usr/bin/env bash
# Run only on a trusted development workstation with Docker, Java 21, Maven and Node 22.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${SPRING_PROFILES_ACTIVE:-}" == *prod* ]]; then
  echo "Refusing to start a development JWT fixture with the production profile." >&2
  exit 1
fi
for dependency in docker java mvn node npm curl; do
  command -v "$dependency" >/dev/null || { echo "Missing prerequisite: $dependency" >&2; exit 1; }
done
echo "[OULA] Starting loopback-only development database"
docker compose -f compose.local.yml up -d --wait
export DB_URL="jdbc:postgresql://127.0.0.1:${OULA_LOCAL_DB_PORT:-5432}/oula"
export DB_USER="oula"
export DB_PASSWORD="oula_dev_only"
export SPRING_PROFILES_ACTIVE="dev-auth"
export OULA_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000"
export OUTBOX_DISPATCH_ENABLED="false"
export SERVER_ADDRESS="127.0.0.1"
export NEXT_PUBLIC_OULA_DEV_AUTH="true"
export NEXT_PUBLIC_OULA_API_BASE_URL="http://127.0.0.1:8080"
export NEXT_TELEMETRY_DISABLED="1"
export OULA_BACKEND_URL="http://127.0.0.1:8080"
export OULA_APP_ORIGIN="http://127.0.0.1:3000"
export OULA_OIDC_ISSUER="http://localhost:8080/dev-auth"
export OULA_OIDC_CLIENT_ID="oula-web"
export OULA_SESSION_SECRET="${OULA_SESSION_SECRET:-$(node -e 'process.stdout.write(require("node:crypto").randomBytes(32).toString("base64url"))')}"
export OULA_ALLOW_HTTP_LOCALHOST="true"
export OULA_LOCAL_DEV_AUTH="true"
API_PID=""
WEB_PID=""
cleanup() {
  [[ -z "$WEB_PID" ]] || kill "$WEB_PID" 2>/dev/null || true
  [[ -z "$API_PID" ]] || kill "$API_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
echo "[OULA] Packaging canonical Java API"
mvn -B -ntp -DskipTests package
java -jar target/oula-*.jar &
API_PID=$!
echo "[OULA] Checking API health"
ready=false
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then ready=true; break; fi
  if ! kill -0 "$API_PID" 2>/dev/null; then echo "Backend exited unexpectedly" >&2; exit 1; fi
  sleep 1
done
[[ "$ready" == "true" ]] || { echo "Backend health check failed" >&2; exit 1; }
(
  cd apps/web
  npm ci --no-audit --no-fund
  npm run build
  npm run start -- -H 127.0.0.1 -p 3000
) &
WEB_PID=$!
echo "[OULA] Connected workspace: http://127.0.0.1:3000/workspace"
echo "[OULA] Gateway: http://127.0.0.1:3000/gateway"
echo "[OULA] Buyer transaction proof: http://127.0.0.1:3000/journey"
echo "[OULA] Manager proof after PR #83 integration: http://127.0.0.1:3000/guardian-proof"
echo "[OULA] This is a fictional DEV_TEST_ONLY demonstration; not production."
wait "$WEB_PID"
