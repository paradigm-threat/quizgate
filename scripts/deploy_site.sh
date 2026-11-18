#!/usr/bin/env bash
# Deploy quizgate.app from the monorepo root.
# Vercel project rootDirectory is "site" — uploading from site/ alone fails with
# NOW_SANDBOX_WORKER_ROOTDIR_NOT_EXIST. Always run this from the repo root.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d site ]]; then
  echo "error: expected $ROOT/site" >&2
  exit 1
fi
if [[ -d site/.vercel ]]; then
  echo "warning: site/.vercel exists — prefer only $ROOT/.vercel (rootDirectory=site)" >&2
fi

SCOPE="${VERCEL_SCOPE:-clevertrees-projects}"
ARGS=(--yes --scope "$SCOPE")
if [[ "${1:-}" == "--prod" || "${1:-}" == "production" || "${1:-}" == "" ]]; then
  ARGS+=(--prod)
  shift || true
fi
ARGS+=("$@")

echo "Deploying quizgate site/ via vercel ${ARGS[*]} (cwd=$ROOT)"
exec vercel "${ARGS[@]}"
