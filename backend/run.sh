#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  echo 'Missing backend/.env. Copy .env.example and fill in your credentials.' >&2
  exit 1
fi
set -a
source .env
set +a

# Prefer JDK 17; the host's default Java 25 cannot run this Gradle version.
swyp_is_java17() {
  [[ -n "${1:-}" && -x "$1/bin/java" ]] && "$1/bin/java" -version 2>&1 | head -n 1 | grep -q 'version "17[.]'
}
if ! swyp_is_java17 "${JAVA_HOME:-}"; then
  JAVA_HOME=""
  if [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -F -v 17 2>/dev/null || true)"
  fi
  if ! swyp_is_java17 "${JAVA_HOME:-}" && [[ -x /tmp/swyp-jdk17/amazon-corretto-17.jdk/Contents/Home/bin/java ]]; then
    JAVA_HOME=/tmp/swyp-jdk17/amazon-corretto-17.jdk/Contents/Home
  fi
fi
if ! swyp_is_java17 "${JAVA_HOME:-}"; then
  echo 'Install JDK 17 and set JAVA_HOME to its installation directory.' >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -f "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
  echo 'GOOGLE_APPLICATION_CREDENTIALS must point to your service-account JSON.' >&2
  exit 1
fi
for swyp_key in NESSIE_API_KEY GEMINI_API_KEY; do
  if [[ -z "${!swyp_key:-}" || "${!swyp_key}" == 'replace-me' ]]; then
    echo "Note: $swyp_key is missing; its features will remain unavailable." >&2
  fi
done
./gradlew installDist --console=plain
exec ./build/install/swyp-backend/bin/swyp-backend "$@"
