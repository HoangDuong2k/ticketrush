#!/usr/bin/env bash
# Sinh file .env với secret ngẫu nhiên cho chạy local. Dùng: scripts/init-env.sh [--force]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env && "${1:-}" != "--force" ]]; then
  echo ".env đã tồn tại. Chạy lại với --force để ghi đè." >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
openssl genpkey -algorithm ed25519 -out "$tmp/qr-signing.pem"

{
  echo "# Sinh bởi scripts/init-env.sh lúc $(date -Iseconds). Không commit file này."
  echo "MOCKPAY_API_KEY=$(openssl rand -hex 16)"
  echo "MOCKPAY_WEBHOOK_SECRET=$(openssl rand -hex 32)"
  echo "JWT_SECRET=$(openssl rand -hex 32)"
  echo "QR_PRIVATE_KEY=$(openssl pkey -in "$tmp/qr-signing.pem" -outform DER | base64 | tr -d '\n')"
  echo "QR_PUBLIC_KEY=$(openssl pkey -in "$tmp/qr-signing.pem" -pubout -outform DER | base64 | tr -d '\n')"
} > .env
chmod 600 .env
echo "Đã tạo .env"
