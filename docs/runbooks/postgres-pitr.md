# Runbook: khôi phục Postgres bằng PITR (C7)

**Khi nào:** xoá nhầm dữ liệu (vd. `DELETE FROM booking.bookings`), hỏng dữ liệu logic, cần quay về một thời điểm. **Mục tiêu:** RTO ≤ 15 phút, mất ≤ 5 phút dữ liệu (RPO, nhờ `archive_timeout: 300`). Liên quan: `overview.md` §18, §20.3, ADR 0033.

Mọi lệnh `kubectl` lên cụm cần báo trước (`CLAUDE.md`). Khôi phục **không ghi đè** cụm đang chạy: dựng cụm mới `tickethub-pg-restore` từ backup, kiểm tra, rồi mới chuyển app sang.

## 0. Chuẩn bị

- Ghi lại **giờ ngay trước khi sự cố** (UTC), vd. `2026-11-05 08:14:00+00`. Lấy từ log app hoặc `psql`: `select max(created_at) from booking.bookings`.
- Dừng ghi vào cụm đang hỏng nếu sự cố còn tiếp diễn: `kubectl -n tickethub scale deploy/app --replicas=0`.
- Xem backup có sẵn: `kubectl -n tickethub get backup` và `kubectl cnpg status tickethub-pg -n tickethub` (mục *Continuous archiving* phải `OK`, có *First recoverability point*).

## 1. Dựng cụm khôi phục

Lưu thành `restore.yaml` (đừng commit vào `infra/k8s-platform/postgres/` vì ArgoCD sẽ đồng bộ nó), thay `targetTime`:

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: tickethub-pg-restore
  namespace: tickethub
spec:
  instances: 1
  imageName: ghcr.io/cloudnative-pg/postgresql:17.6
  storage: { size: 5Gi }
  bootstrap:
    recovery:
      source: tickethub-pg
      recoveryTarget:
        targetTime: "2026-11-05 08:14:00+00"
  externalClusters:
    - name: tickethub-pg
      barmanObjectStore:
        destinationPath: s3://tickethub-backups/postgres
        endpointURL: http://backup-s3.tickethub.svc:3900
        s3Credentials:
          accessKeyId: { name: tickethub-backup-s3, key: ACCESS_KEY_ID }
          secretAccessKey: { name: tickethub-backup-s3, key: ACCESS_SECRET_KEY }
        wal: { maxParallel: 2 }
```

```bash
kubectl apply -f restore.yaml
kubectl -n tickethub wait --for=condition=Ready cluster/tickethub-pg-restore --timeout=10m
```

## 2. Kiểm tra dữ liệu

```bash
kubectl -n tickethub exec -it tickethub-pg-restore-1 -c postgres -- psql -d tickethub \
  -c "select count(*), max(created_at) from booking.bookings"
```

Số dòng và mốc thời gian phải khớp kỳ vọng (dữ liệu bị xoá đã có lại, không có dữ liệu sau giờ chọn).

## 3. Chuyển app sang cụm khôi phục

Hai cách, chọn một:

1. **Chép lại phần bị mất** (nhanh nhất khi chỉ mất một bảng): `pg_dump -t booking.bookings` từ cụm khôi phục rồi nạp vào cụm chính. Dùng khi dữ liệu sau sự cố ở cụm chính còn nguyên.
2. **Thay cụm** (khi hỏng nặng): đổi Service để `tickethub-pg-rw` trỏ cụm khôi phục, hoặc đổi `DB_URL` của app và Keycloak sang `tickethub-pg-restore-rw`, rồi `kubectl -n tickethub scale deploy/app --replicas=1`.

Sau khi ổn định, tạo lại cụm chính từ cụm khôi phục (hoặc từ backup mới), bật lại `ScheduledBackup`, xoá cụm tạm.

## 4. Sau khi xong

- Kiểm: `GET /actuator/health/readiness` của app `UP`; chạy thử một luồng mua vé trên staging.
- Ghi postmortem ở `docs/postmortems/` (nguyên nhân, mốc giờ, thời gian khôi phục thực tế so với RTO 15 phút, dữ liệu mất so với RPO 5 phút).

## Giới hạn

- Backup nằm cùng cụm staging: mất cả máy thì mất cả backup. Chép định kỳ bucket `tickethub-backups` ra ngoài máy là việc riêng.
- Đường dẫn `destinationPath` / `endpointURL` phải khớp `infra/k8s-platform/postgres/cluster.yaml`.
