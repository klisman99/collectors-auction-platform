#!/usr/bin/env bash
# Exercise the real proof entrypoint with a failing Compose startup.
set -euo pipefail
repository=$(cd "$(dirname "$0")/../.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts" "$fixture/bin"
cp "$repository/scripts/prove-mvp.sh" "$fixture/scripts/"
cat > "$fixture/bin/docker" <<'STUB'
#!/usr/bin/env bash
case "$1" in
  version|info) echo 'test Docker environment' ;;
  compose)
    case " $* " in
      *' up '*) echo 'proof-startup-regression: image pull failed' >&2; exit 42 ;;
      *' ps '*) echo 'no containers' ;;
      *' down '*) touch "$PROOF_STARTUP_FIXTURE/cleaned" ;;
      *) exit 99 ;;
    esac ;;
  *) exit 99 ;;
esac
STUB
cat > "$fixture/bin/git" <<'STUB'
#!/usr/bin/env bash
echo 'test source revision'
STUB
chmod +x "$fixture/bin/docker" "$fixture/bin/git"
status=0
PATH="$fixture/bin:$PATH" PROOF_STARTUP_FIXTURE="$fixture" KEEP_PROOF_STACK=0 \
  bash "$fixture/scripts/prove-mvp.sh" > "$fixture/output" 2>&1 || status=$?
[[ $status == 42 ]] || { echo "Startup exit status was lost: $status" >&2; exit 1; }
message='proof-startup-regression: image pull failed'
grep -Fq "$message" "$fixture/output" || { echo 'Startup failure is missing from CI output' >&2; exit 1; }
grep -Fq "$message" "$fixture/artifacts/proof/build.log"
[[ -f "$fixture/cleaned" ]] || { echo 'Disposable project cleanup was skipped' >&2; exit 1; }
echo 'Proof startup failure is visible, preserves its exit status, and cleans up.'
