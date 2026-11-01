#!/usr/bin/env bash
set -euo pipefail
production_ref=refs/heads/codex/online-core-compartilhado
if [[ "${GITHUB_EVENT_NAME:-}" != push || "${GITHUB_REF:-}" != "$production_ref" ]]; then
 echo 'Fly deploy skipped: only production pushes can deploy.'
 exit 0
fi
latest=$(git ls-remote origin "$production_ref" | cut -f1)
if [[ -z "$latest" ]]; then
 echo 'Unable to resolve the production branch.' >&2; exit 1
fi
if [[ "$latest" != "$GITHUB_SHA" ]]; then
 echo 'Fly deploy skipped: a newer commit is already on production.'
 exit 0
fi
if [[ -z "${FLY_API_TOKEN:-}" ]]; then
 echo 'Configure the app-scoped FLY_API_TOKEN Actions secret before enabling deployment.' >&2; exit 1
fi
flyctl deploy --config fly.toml --app colheita-feliz-public --ha=false --update-only --no-public-ips \
 --remote-only --depot=false --image-label "deployment-$GITHUB_SHA" \
 --label "org.opencontainers.image.revision=$GITHUB_SHA" --yes
curl --fail --silent --show-error --retry 8 --retry-delay 5 --retry-all-errors --max-time 15 \
 https://colheita-feliz-public.fly.dev/health | node -e '
 let body="";process.stdin.on("data",chunk=>body+=chunk);
 process.stdin.on("end",()=>{const health=JSON.parse(body);
  if(health.ok!==true||health.development!==false)process.exit(1);
  console.log("Production API health check passed.");});'
