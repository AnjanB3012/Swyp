#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  cp .env.example .env
fi

replace_env_value() {
  local swyp_key="$1"
  local swyp_value="$2"
  local swyp_tmp
  swyp_tmp="$(mktemp "${TMPDIR:-/tmp}/swyp-env.XXXXXX")"
  chmod 600 "$swyp_tmp"

  awk -v key="$swyp_key" -v value="$swyp_value" '
    BEGIN { replaced = 0 }
    index($0, key "=") == 1 {
      print key "=" value
      replaced = 1
      next
    }
    { print }
    END {
      if (!replaced) print key "=" value
    }
  ' .env > "$swyp_tmp"

  mv "$swyp_tmp" .env
  chmod 600 .env
}

read -r -s -p "Nessie API key: " swyp_nessie_key
printf '\n'
read -r -s -p "Gemini API key: " swyp_gemini_key
printf '\n'

if [[ -z "$swyp_nessie_key" || -z "$swyp_gemini_key" ]]; then
  echo "Both keys are required; backend/.env was not changed." >&2
  exit 1
fi

replace_env_value NESSIE_API_KEY "$swyp_nessie_key"
replace_env_value GEMINI_API_KEY "$swyp_gemini_key"

unset swyp_nessie_key swyp_gemini_key
echo "Saved the Nessie and Gemini keys in backend/.env with owner-only permissions."
echo "Run ./backend/run.sh from the repository root to start Swyp."
