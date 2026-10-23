#!/bin/sh
# C8 (overview.md §20.3): đầy disk volume Postgres. Chaos Mesh không có thí nghiệm đầy disk nên ghi file ballast vào
# volume dữ liệu của pod chỉ định rồi theo dõi mức dùng. Kỳ vọng: alert disk > 80% bắn trước khi Postgres hỏng.
#
#   sh infra/chaos/c8-fill-postgres-disk.sh fill  tickethub-pg-1 85    # lấp tới 85% dung lượng volume
#   sh infra/chaos/c8-fill-postgres-disk.sh clear tickethub-pg-1       # xoá ballast
#
# Chỉ chạy trên staging k3d, trên một replica (không phải primary) trừ khi cố ý diễn tập primary đầy disk.
set -eu

ACTION="${1:?fill|clear}"
POD="${2:?tên pod postgres}"
TARGET="${3:-85}"
NS=tickethub
DATA=/var/lib/postgresql/data
BALLAST="$DATA/chaos-ballast"

case "$ACTION" in
  fill)
    USED_PCT="$(kubectl -n "$NS" exec "$POD" -c postgres -- df --output=pcent "$DATA" | tail -n 1 | tr -dc '0-9')"
    TOTAL_KB="$(kubectl -n "$NS" exec "$POD" -c postgres -- df --output=size "$DATA" | tail -n 1 | tr -dc '0-9')"
    if [ "$USED_PCT" -ge "$TARGET" ]; then
      echo "Volume đã dùng ${USED_PCT}%, không cần lấp thêm."
      exit 0
    fi
    ADD_KB=$(( TOTAL_KB * (TARGET - USED_PCT) / 100 ))
    echo "Lấp thêm ${ADD_KB} KB để đạt ${TARGET}% trên ${POD}."
    kubectl -n "$NS" exec "$POD" -c postgres -- fallocate -l "${ADD_KB}K" "$BALLAST"
    ;;
  clear)
    kubectl -n "$NS" exec "$POD" -c postgres -- rm -f "$BALLAST"
    ;;
  *)
    echo "Dùng: fill|clear" >&2
    exit 2
    ;;
esac

kubectl -n "$NS" exec "$POD" -c postgres -- df -h "$DATA"
