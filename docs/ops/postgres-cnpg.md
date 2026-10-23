# Postgres trên k3d bằng CloudNativePG

Phạm vi Sprint 4 việc 9. Mọi lệnh `kubectl` / `helm` lên cụm cần báo trước (`CLAUDE.md`). Thiết kế: `overview.md` §18, ADR 0013, ADR 0033 (object storage cho backup).

## Kiến trúc

```
cnpg-system: operator CloudNativePG (ArgoCD app cnpg-operator, wave 0)
tickethub:   Cluster tickethub-pg (3 instance, wave 1) ── Service tickethub-pg-rw / -ro
                │  WAL archive liên tục (archive_timeout 5 phút) + base backup 02:00 hằng ngày
                └─▶ object storage tương thích S3 (bucket tickethub-backups), giữ 7 ngày
app, Keycloak ──▶ tickethub-pg-rw:5432
```

Chart `core` không còn Postgres; app (`DB_URL`) và Keycloak (`KC_DB_URL`) trỏ `tickethub-pg-rw`.

## Secret cần tạo trước (không commit)

```bash
kubectl -n tickethub create secret generic tickethub-db-auth --type=kubernetes.io/basic-auth \
  --from-literal=username=tickethub --from-literal=password=<...>
kubectl -n tickethub create secret generic tickethub-db --from-literal=password=<cùng mật khẩu>
kubectl -n tickethub create secret generic tickethub-backup-s3 \
  --from-literal=ACCESS_KEY_ID=<...> --from-literal=ACCESS_SECRET_KEY=<...>
```

## Dựng

ArgoCD đồng bộ hai app mới (`cnpg-operator`, `postgres`). Hoặc làm tay lần đầu: `helm repo add cnpg https://cloudnative-pg.github.io/charts && helm upgrade --install cnpg cnpg/cloudnative-pg -n cnpg-system --create-namespace --version 0.26.0`, rồi `kubectl apply -f infra/k8s-platform/postgres/cluster.yaml`.

Backup chỉ chạy được sau khi dựng object storage theo phương án đã chọn ở ADR 0033 và đặt đúng `endpointURL` trong `cluster.yaml`. Trước đó cụm vẫn chạy bình thường nhưng `Continuous archiving` báo lỗi.

## Kiểm chứng

| Việc | Lệnh | Kỳ vọng |
|---|---|---|
| Cụm lên | `kubectl cnpg status tickethub-pg -n tickethub` | 3 instance `Ready`, có primary |
| Archive WAL | cùng lệnh, mục *Continuous archiving* | `OK` |
| Backup | `kubectl -n tickethub get backup` | `completed` sau lần `ScheduledBackup` đầu |
| Schema | `psql -d tickethub -c '\dn'` | có 7 schema của module |
| Failover (C1) | `docs/runbooks/postgres-failover.md` | < 30 giây |
| PITR (C7) | `docs/runbooks/postgres-pitr.md` | RTO ≤ 15 phút, mất ≤ 5 phút dữ liệu |

## Rollback

Đưa Postgres cũ về là khôi phục từ `pg_dump`, không đảo ngược tự động. Quay lui việc dựng: `kubectl delete -f infra/k8s-platform/postgres/cluster.yaml` (xoá cả PVC dữ liệu, cần xác nhận) rồi `helm uninstall cnpg -n cnpg-system`.

## Chưa làm

- PgBouncer (`Pooler`): HikariCP của app đã gom kết nối; thêm khi cần.
- `enablePodMonitor` bật ở việc 10 khi có Prometheus Operator.
- Bản sao backup ra ngoài máy staging.
