#!/bin/sh
# Tạo N user test trong Keycloak (mặc định 500) và ghi users.csv cho JMeter.
# Chạy với Keycloak của STAGING hoặc dev, không bao giờ với dữ liệu thật (CLAUDE.md). Idempotent: user đã có thì bỏ qua.
#
#   KEYCLOAK_URL=https://auth.tickethub.localhost KEYCLOAK_ADMIN_PASSWORD=... sh seed-users.sh [số user] [users.csv]
#
# Cần curl. Mật khẩu admin lấy từ biến môi trường, không ghi vào file.
set -eu

COUNT="${1:-500}"
OUT="${2:-users.csv}"
BASE="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${KEYCLOAK_REALM:-tickethub}"
ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:?đặt KEYCLOAK_ADMIN_PASSWORD}"
USER_PASSWORD="${LOADTEST_PASSWORD:-Load-Test-1234}"
CURL="curl -sS -k"

TOKEN="$($CURL -d grant_type=password -d client_id=admin-cli -d "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASSWORD" "$BASE/realms/master/protocol/openid-connect/token" |
  sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
[ -n "$TOKEN" ] || { echo "Không lấy được token admin" >&2; exit 1; }

echo "username,password" > "$OUT"
i=1
while [ "$i" -le "$COUNT" ]; do
  NAME="$(printf 'loadtest-%04d' "$i")"
  STATUS="$($CURL -o /dev/null -w '%{http_code}' -X POST "$BASE/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$NAME\",\"email\":\"$NAME@loadtest.tickethub.local\",\"emailVerified\":true,\"enabled\":true,\"firstName\":\"Load\",\"lastName\":\"Test\",\"credentials\":[{\"type\":\"password\",\"value\":\"$USER_PASSWORD\",\"temporary\":false}]}")"
  case "$STATUS" in
    201|409) ;;
    *) echo "Tạo $NAME thất bại: HTTP $STATUS" >&2; exit 1 ;;
  esac
  echo "$NAME,$USER_PASSWORD" >> "$OUT"
  i=$((i + 1))
done
echo "Đã ghi $COUNT user vào $OUT"
