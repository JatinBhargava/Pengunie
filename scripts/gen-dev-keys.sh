#!/usr/bin/env bash
# Generates an RSA key pair for signing access tokens and writes it to backend/.env.local
# (git-ignored). Without keys the backend generates an ephemeral pair on every start, which logs
# everyone out on restart.
set -euo pipefail
cd "$(dirname "$0")/.."

out=backend/.env.local
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
openssl pkey -in "$tmp/private.pem" -pubout -out "$tmp/public.pem"

touch "$out"
grep -v -E '^AUTH_(PRIVATE|PUBLIC)_KEY=' "$out" > "$tmp/env" || true
{
  cat "$tmp/env"
  echo "AUTH_PRIVATE_KEY=\"$(awk 'NF {printf "%s\\n", $0}' "$tmp/private.pem")\""
  echo "AUTH_PUBLIC_KEY=\"$(awk 'NF {printf "%s\\n", $0}' "$tmp/public.pem")\""
} > "$out"
echo "Wrote signing keys to $out"
