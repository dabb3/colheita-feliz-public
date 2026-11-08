#!/usr/bin/env bash
set -euo pipefail
script="$(dirname "$0")/deploy-fly.sh"
export DEPLOY_TEST_LOG
DEPLOY_TEST_LOG=$(mktemp)
trap 'rm -f -- "$DEPLOY_TEST_LOG"' EXIT

# Replace every external service command; no test can deploy or contact Fly.io.
git() { printf '%s\trefs/heads/codex/online-core-compartilhado\n' "$MOCK_LATEST"; }
flyctl() { printf 'deploy %s\n' "$*" >> "$DEPLOY_TEST_LOG"; return "$MOCK_FLY_STATUS"; }
curl() { printf '%s' "$MOCK_HEALTH"; }
export -f git flyctl curl
export GITHUB_EVENT_NAME=push GITHUB_REF=refs/heads/codex/online-core-compartilhado
export GITHUB_SHA=1111111111111111111111111111111111111111
export MOCK_LATEST="$GITHUB_SHA" FLY_API_TOKEN=test-only MOCK_FLY_STATUS=0
export MOCK_HEALTH='{"ok":true,"development":false}'

check() {
 local name="$1" expected_status="$2" expected_deploys="$3" status=0
 : > "$DEPLOY_TEST_LOG"
 bash "$script" > /dev/null 2>&1 || status=$?
 if [[ "$status" != "$expected_status" || "$(grep -c '^deploy ' "$DEPLOY_TEST_LOG" || true)" != "$expected_deploys" ]]; then
  echo "FAIL: $name (status=$status)"; exit 1
 fi
 echo "PASS: $name"
}
check production 0 1
grep -Fq -- '--app colheita-feliz-public --ha=false --update-only --no-public-ips' "$DEPLOY_TEST_LOG"
expected_version=$(node -p 'JSON.parse(require("node:fs").readFileSync("assets/data/update-notes.json","utf8")).version')
expected_pr=$(node -p 'JSON.parse(require("node:fs").readFileSync("assets/data/update-notes.json","utf8")).prNumber')
grep -Fq -- "--env PUBLISHED_UPDATE_NOTES_VERSION=$expected_version --env PUBLISHED_UPDATE_NOTES_PR=$expected_pr" "$DEPLOY_TEST_LOG"
echo 'PASS: publication metadata follows the manifest'
GITHUB_EVENT_NAME=pull_request check pull-request 0 0
GITHUB_REF=refs/heads/feature check feature-branch 0 0
GITHUB_REF=refs/tags/v1.0.0 check tag 0 0
MOCK_LATEST=2222222222222222222222222222222222222222 check superseded-commit 0 0
MOCK_LATEST='' check missing-branch 1 0
FLY_API_TOKEN='' check missing-token 1 0
MOCK_FLY_STATUS=1 check deployment-failure 1 1
MOCK_HEALTH='{"ok":false,"development":false}' check unhealthy-api 1 1
MOCK_HEALTH='{"ok":true,"development":true}' check development-api 1 1

node() { return 1; }
export -f node
check invalid-notes-manifest 1 0
unset -f node
node --test "$(dirname "$0")/published-notes-env.test.mjs"
