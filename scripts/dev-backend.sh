#!/usr/bin/env bash
# Runs the API with variables from backend/.env.local. Usage: scripts/dev-backend.sh [profile]
# Profiles: fake (default, no API key), openai, anthropic, ollama.
set -euo pipefail
cd "$(dirname "$0")/../backend"
if [[ -f .env.local ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.local
  set +a
fi
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="${1:-fake}"
