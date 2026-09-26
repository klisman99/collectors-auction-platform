#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ ${E2E_COMPOSE_PROJECT:-} == collectors-proof-* ]] || { echo 'A disposable proof project is required.' >&2; exit 1; }
source_project="$E2E_COMPOSE_PROJECT"
restore_project="${source_project}-restore"
source_compose=(docker compose --env-file /dev/null -p "$source_project")
restore_compose=(docker compose --env-file /dev/null -p "$restore_project")
umask 077
backup_dir=$(mktemp -d)
started_at=$(date -u +%FT%TZ)
started_seconds=$SECONDS
cleanup_backup() {
  status=$?
  "${source_compose[@]}" start seaweedfs backend web > /dev/null 2>&1 || true
  "${restore_compose[@]}" down --volumes --remove-orphans > /dev/null 2>&1 || true
  rm -rf "$backup_dir"
  exit "$status"
}
trap cleanup_backup EXIT
"${source_compose[@]}" stop web backend seaweedfs > /dev/null
"${source_compose[@]}" exec -T postgres pg_dump -U collectors -d collectors_auction -Fc > "$backup_dir/database.dump"
source_seaweedfs=$("${source_compose[@]}" ps --all --quiet seaweedfs)
docker cp "$source_seaweedfs:/data/." - > "$backup_dir/seaweedfs.tar"
counts="SELECT 'bids',count(*) FROM accepted_bids UNION ALL SELECT 'sales',count(*) FROM sales UNION ALL SELECT 'audit',count(*) FROM audit_records UNION ALL SELECT 'media',count(*) FROM collectible_item_media ORDER BY 1;"
"${source_compose[@]}" exec -T postgres psql -X -qAt -U collectors -d collectors_auction -c "$counts" > "$backup_dir/before.txt"
# The restore uses a distinct namespace and ports and reuses the proven images.
export WEB_PORT=28080 POSTGRES_PORT=25432 S3_API_PORT=28333
export MAILPIT_SMTP_PORT=21025 MAILPIT_WEB_PORT=28025
# Reuse images without rebuilding or relying on the default project's images.
docker tag "$source_project-backend" "$restore_project-backend"
docker tag "$source_project-web" "$restore_project-web"
"${restore_compose[@]}" up -d postgres > /dev/null
for attempt in {1..60}; do
  if "${restore_compose[@]}" exec -T postgres pg_isready -h 127.0.0.1 -U collectors -d collectors_auction > /dev/null; then break; fi
  sleep 1
done
"${restore_compose[@]}" exec -T postgres pg_restore -U collectors -d collectors_auction --exit-on-error < "$backup_dir/database.dump"
"${restore_compose[@]}" create seaweedfs > /dev/null
restore_seaweedfs=$("${restore_compose[@]}" ps --all --quiet seaweedfs)
docker cp - "$restore_seaweedfs:/data" < "$backup_dir/seaweedfs.tar"
docker cp "$restore_seaweedfs:/data/." - > "$backup_dir/restored-seaweedfs.tar"
# Compare every restored file by path and SHA-256, independently of tar ordering.
python3 - "$backup_dir/seaweedfs.tar" "$backup_dir/restored-seaweedfs.tar" <<'PY'
import hashlib, sys, tarfile

def manifest(path):
    with tarfile.open(path) as archive:
        return {entry.name: hashlib.sha256(archive.extractfile(entry).read()).hexdigest()
                for entry in archive if entry.isfile()}
assert manifest(sys.argv[1]) == manifest(sys.argv[2]), 'Restored private object bytes differ'
PY
"${restore_compose[@]}" up -d --no-build > /dev/null
for attempt in {1..120}; do
  if curl --fail --silent http://127.0.0.1:28080/actuator/health/readiness > /dev/null; then break; fi
  sleep 1
done
curl --fail --silent http://127.0.0.1:28080/actuator/health/readiness > /dev/null
"${restore_compose[@]}" exec -T postgres psql -X -qAt -U collectors -d collectors_auction -c "$counts" > "$backup_dir/after.txt"
cmp "$backup_dir/before.txt" "$backup_dir/after.txt"
python3 - "$started_at" "$((SECONDS-started_seconds))" "$backup_dir/before.txt" <<'PY'
import json, pathlib, sys
pathlib.Path('artifacts/proof/backup.json').write_text(json.dumps({
    'startedAt': sys.argv[1], 'recoverySeconds': int(sys.argv[2]),
    'domainCounts': pathlib.Path(sys.argv[3]).read_text().splitlines(),
    'allPrivateObjectBytesRestored': True, 'restoredApplicationReady': True,
    'recoveryPoint': 'cold snapshot with writers stopped'
}, indent=2) + '\n')
PY
