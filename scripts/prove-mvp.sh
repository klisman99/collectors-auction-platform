#!/usr/bin/env bash
# Disposable local/CI deployment. Never point recovery experiments at user data.
set -euo pipefail
cd "$(dirname "$0")/.."
export E2E_COMPOSE_PROJECT="collectors-proof-${GITHUB_RUN_ID:-$(date +%s)}"
export COMPOSE_PROJECT_NAME="$E2E_COMPOSE_PROJECT"
export WEB_PORT=18080 POSTGRES_PORT=15432 S3_API_PORT=18333
export MAILPIT_SMTP_PORT=11025 MAILPIT_WEB_PORT=18025
export PROMETHEUS_PORT=19090 GRAFANA_PORT=13000 TEMPO_PORT=13200 OTLP_PORT=14318
export POSTGRES_DB=collectors_auction POSTGRES_USER=collectors POSTGRES_PASSWORD=collectors
export S3_ACCESS_KEY=collectors S3_SECRET_KEY=collectors-local-secret S3_BUCKET=collectors-images
export INITIAL_ADMINISTRATOR_EMAIL=admin-proof@example.com
export INITIAL_ADMINISTRATOR_PASSWORD='operational proof administrator password'
export BASE_URL=http://127.0.0.1:18080 MAILPIT_URL=http://127.0.0.1:18025
export OTEL_TRACING_ENABLED=true
export OPENAPI_URL="$BASE_URL/v3/api-docs"
# Use the configured pnpm directly in CI, or the repository's Corepack shim locally.
if command -v pnpm >/dev/null; then
  package_manager=(pnpm)
else
  package_manager=(corepack pnpm)
fi
mkdir -p artifacts/proof
# A failed startup must not leave successful evidence from an earlier run.
rm -f artifacts/proof/{environment.txt,build.log,results.json,readiness.json,prometheus.json,containers.txt,backup.json}
compose=(docker compose --env-file /dev/null -p "$E2E_COMPOSE_PROJECT" --profile observability)
cleanup() {
  status=$?
  # Raw logs may contain third-party exception payloads; secret scanning occurs
  # in the test before any diagnostic material is persisted.
  "${compose[@]}" ps --all > artifacts/proof/containers.txt || true
  if [[ ${KEEP_PROOF_STACK:-0} != 1 ]]; then
    "${compose[@]}" down --volumes --remove-orphans > /dev/null
  else
    printf 'Retained proof project: %s\n' "$E2E_COMPOSE_PROJECT"
  fi
  exit "$status"
}
trap cleanup EXIT
{
  git rev-parse HEAD
  git diff --stat
  git status --short
  uname -sm
  docker version --format '{{.Server.Version}}'
  docker info --format 'CPUs={{.NCPU}} MemoryBytes={{.MemTotal}}'
  node --version
} > artifacts/proof/environment.txt
# Keep build/pull failures visible in CI; pipefail preserves Compose's status.
# This contains startup output, not backend request logs or authentication traces.
"${compose[@]}" up --detach --build 2>&1 | tee artifacts/proof/build.log
for attempt in {1..120}; do
  if curl --fail --silent "$BASE_URL/actuator/health/readiness" > /dev/null; then break; fi
  sleep 2
done
curl --fail --silent "$BASE_URL/actuator/health/readiness" > artifacts/proof/readiness.json
verification_status=0
"${package_manager[@]}" --dir web run contract:check || verification_status=1
"${package_manager[@]}" --dir web run e2e || verification_status=1
"${package_manager[@]}" --dir web run proof || verification_status=1
# Scraping must work, not merely have a configured dashboard.
curl --fail --silent 'http://127.0.0.1:19090/api/v1/query?query=up' > artifacts/proof/prometheus.json
node --input-type=module -e "import fs from 'node:fs'; const r=JSON.parse(fs.readFileSync('artifacts/proof/prometheus.json')); if(!r.data.result.some(s=>s.metric.job==='collectors-auction-platform' && s.value[1]==='1')) process.exit(1);"
git diff --exit-code -- web/src/api/generated

bash scripts/prove-backup.sh || verification_status=1
exit "$verification_status"
