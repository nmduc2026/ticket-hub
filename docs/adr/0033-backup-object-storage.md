# 0033. Object storage tương thích S3 cho backup Postgres trên staging

**Trạng thái:** Proposed (chờ chọn phương án)

## Bối cảnh
Backup Postgres (WAL archive + base backup, `overview.md` §18) cần object storage tương thích S3 chạy **trên staging**, không dùng S3 của AWS vì AWS chỉ dùng cho lab tạm thời. MinIO đã ngừng phát hành image nên không dùng. Máy staging 32GB chạy k3d, dự án chi phí ~0$. Công cụ ghi backup là barman-cloud của CloudNativePG: cần endpoint S3 (path-style), bucket, access key.

## Các phương án

| Phương án | Ưu | Nhược |
|---|---|---|
| **A. Garage** (Deuxfleurs) | Rất nhẹ (một binary Rust, ~50 MB RAM), image chính thức, đủ API S3 cho barman | Giấy phép AGPLv3 (chỉ dùng nội bộ nên không vướng); phải chạy lệnh khai báo layout một lần sau khi dựng |
| **B. SeaweedFS** (chế độ S3 gateway) | Giấy phép Apache 2.0, một lệnh `weed server -s3` là chạy, có UI | Nhiều thành phần hơn (master, volume, filer), tốn RAM hơn; ít người dùng làm đích barman hơn |
| **C. Rook-Ceph (RGW)** | Giống production nhất, nhân bản dữ liệu | Quá nặng cho 32GB, nhiều tuần để học vận hành |
| **D. Adobe S3Mock** (đang dùng cho ảnh banner) | Có sẵn trong chart `core` | Lưu trong bộ nhớ / đĩa tạm, không bền; không phải nơi đặt backup |

## Đề xuất
**A. Garage**: nhẹ nhất, đủ cho barman-cloud, và bước khai báo layout chỉ làm một lần (ghi trong runbook). Chọn B nếu muốn giấy phép Apache 2.0 và chấp nhận tốn RAM.

## Hệ quả (khi chọn)
- Viết chart / manifest dựng storage, bucket `tickethub-backups`, Secret `tickethub-backup-s3` (`ACCESS_KEY_ID`, `ACCESS_SECRET_KEY`).
- Đặt `endpointURL` trong `infra/k8s-platform/postgres/cluster.yaml` đúng Service của phương án đã chọn (hiện để tên giữ chỗ `backup-s3.tickethub.svc:3900`).
- Backup chỉ nằm trên cùng cụm nên **không** chống được mất cả máy staging; bản sao ra ngoài máy là việc riêng (ghi ở `docs/runbooks/postgres-pitr.md`, mục giới hạn).

Liên quan: `overview.md` §18, §20.3 (C7), ADR 0013.
