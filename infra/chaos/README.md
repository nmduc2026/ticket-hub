# Diễn tập sự cố (Chaos Mesh)

Thí nghiệm cho các sự cố C2, C4, C8 của `overview.md` §20.3 (Sprint 5 hạng mục 14). Chaos Mesh cài bằng ArgoCD (`argocd/apps/chaos-mesh.yaml`, namespace `chaos-mesh`); thí nghiệm **không** nằm trong ArgoCD: áp tay bằng `kubectl` trong lúc diễn tập, gỡ ngay sau đó. Chỉ chạy trên staging k3d.

| Sự cố | File | Cách gây | Alert kỳ vọng |
|---|---|---|---|
| C2 Stripe chậm 3 giây | `c2-stripe-delay.yaml` | NetworkChaos delay tới stripe-mock | circuit breaker Stripe mở (burn rate nếu kéo dài) |
| C4 Redis chết | `c4-redis-kill.yaml` (cả cụm), `c4-redis-kill-master.yaml` (chỉ master) | PodChaos pod-kill | `SeatHoldUnavailable` |
| C8 đầy disk Postgres | `c8-fill-postgres-disk.sh` | ghi file ballast tới >80% | alert disk (kube-prometheus-stack) |
| C8 rò bộ nhớ JVM | profile Spring `chaos`, `POST /api/v1/chaos/memory/leak?megabytes=64` (ADMIN) | giữ khối byte trong heap | alert heap cao |

## Mỗi lần diễn tập

1. Ghi trạng thái ban đầu (alert đang im, dashboard).
2. Gây lỗi → đo thời gian tới khi alert kêu (Telegram) → chẩn đoán theo runbook → khắc phục.
3. Viết hoặc bổ sung runbook ở `docs/runbooks/` (đã có khung `seat-hold-unavailable.md` cho C4) và postmortem ở `docs/postmortems/` (mốc thời gian, nguyên nhân, cái làm tốt, cái cần sửa, việc theo dõi).
4. C4 phải chạy kèm Flash sale (JMeter, `load-tests/jmeter/`): kiểm truy vấn ghế trùng §10 = 0 sau khi xong.

## C8: rò bộ nhớ và heap dump

Bật profile `chaos` cho pod `app` (biến `SPRING_PROFILES_ACTIVE=chaos`), gọi endpoint leak vài lần cho tới khi alert heap kêu, lấy heap dump:

```bash
kubectl -n tickethub exec <pod-app> -- jcmd 1 GC.heap_dump /tmp/heap.hprof   # image distroless: dùng `kubectl debug` với image JDK nếu không có jcmd
kubectl -n tickethub cp <pod-app>:/tmp/heap.hprof ./heap.hprof
```

Phân tích bằng Eclipse MAT (Leak Suspects) và ghi vào postmortem. Gỡ: `DELETE /api/v1/chaos/memory/leak`, bỏ profile `chaos`.

## Trạng thái

Chưa chạy thật trên cụm (chờ k3d, mục "Việc treo của Sprint 5" ở `docs/plans/2026-10-21-sprint-cleanup-plan.md`). Version chart `chaos-mesh` 2.7.2 theo trí nhớ: kiểm bằng `helm search repo chaos-mesh/chaos-mesh --versions`.
