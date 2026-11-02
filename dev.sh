#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

api_pid=
web_pid=
cleanup() {
  [[ -z "$api_pid" ]] || kill "$api_pid" 2>/dev/null || true
  [[ -z "$web_pid" ]] || kill "$web_pid" 2>/dev/null || true
  [[ -z "$api_pid" ]] || wait "$api_pid" 2>/dev/null || true
  [[ -z "$web_pid" ]] || wait "$web_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker compose up -d --wait postgres

if [[ ! -d web/node_modules ]]; then
  npm ci --ignore-scripts --prefix web
fi
npm run build:local --prefix web

AUTH_MODE=development \
HOST=127.0.0.1 \
DATABASE_URL=jdbc:postgresql://127.0.0.1:55439/colheita_local \
DATABASE_USER=postgres \
ALLOWED_ORIGINS=http://127.0.0.1:5173 \
GRADLE_USER_HOME="$PWD/.gradle" bash ./gradlew --no-daemon :server:run &
api_pid=$!
npm run serve --prefix web &
web_pid=$!

for _ in {1..60}; do
  if curl --fail --silent http://127.0.0.1:8080/health >/dev/null; then
    break
  fi
  if ! kill -0 "$api_pid" 2>/dev/null; then
    wait "$api_pid"
  fi
  sleep 1
done

if ! curl --fail --silent http://127.0.0.1:8080/health >/dev/null; then
  echo "A API não iniciou em http://127.0.0.1:8080" >&2
  exit 1
fi

echo "Web: http://127.0.0.1:5173"
echo "API: http://127.0.0.1:8080 (health: /health)"
echo "Contas locais: Alice (Bearer dev-alice) e Bob (Bearer dev-bob)"
echo "PostgreSQL permanece ativo após Ctrl+C; pare-o com: docker compose stop postgres"

wait -n "$api_pid" "$web_pid"
