# Runbook: chứng chỉ sắp hết hạn / không Ready (C9; alert `CertificateExpiringSoon`, `CertificateNotReady`)

cert-manager tự gia hạn khi còn 1/3 vòng đời (hoặc `renewBefore`). Alert bắn nghĩa là việc gia hạn không diễn ra. Liên quan: `overview.md` §20.3 C9.

## Xử lý

```bash
kubectl get certificate -A
kubectl -n <ns> describe certificate <tên>      # xem Events, điều kiện Ready
kubectl -n cert-manager logs deploy/cert-manager --tail=100
kubectl get clusterissuer tickethub-ca -o yaml  # Issuer Ready?
```

Nguyên nhân thường gặp: cert-manager bị tắt/lỗi, ClusterIssuer không Ready (Secret CA mất), hết quota nếu dùng ACME.
Ép gia hạn: `cmctl renew <tên> -n <ns>` hoặc xoá Secret TLS (cert-manager cấp lại). Báo trước lệnh lên cụm.

## Diễn tập (C9) với `infra/k8s-platform/observability/cert-drill.yaml`

1. Chứng chỉ `c9-short-lived` hạn 1 giờ, `renewBefore` 40 phút: sau ~20 phút `status.notAfter` đổi mà không can thiệp.
2. Thử alert: `kubectl -n cert-manager scale deploy cert-manager --replicas=0`; hạn 1 giờ luôn < 7 ngày nên `CertificateExpiringSoon` bắn sau 10 phút (metric còn giữ giá trị cuối từ lần scrape trước khi tắt thì rule vẫn thấy; nếu không, tạm đổi ngưỡng). Bật lại `--replicas=1` và xác nhận alert resolved.

## Điều kiện kiểm chứng

Tự gia hạn thấy `notAfter` mới; alert tới Telegram kèm link runbook này.
