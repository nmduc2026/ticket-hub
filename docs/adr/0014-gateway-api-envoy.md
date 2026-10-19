# 0014. Ingress: Gateway API + Envoy Gateway

**Trạng thái:** Accepted

## Bối cảnh
Cluster (k3d ở staging, k3s ở AWS lab) cần điểm vào HTTP(S) từ bên ngoài, có TLS (cert-manager), định tuyến tới frontend và Gateway BFF.

## Các phương án
- **ingress-nginx:** phổ biến nhất trước đây, nhưng đã ngừng bảo trì.
- **Gateway API + Envoy Gateway:** chuẩn mới thay cho Ingress API của Kubernetes, tách vai trò hạ tầng và định tuyến rõ ràng.

## Quyết định
**Gateway API** với implementation **Envoy Gateway**. TLS bằng cert-manager. Chỉ Gateway BFF nhận traffic từ ngoài (NetworkPolicy); Gateway BFF tin header `X-Forwarded-*` nên chỉ được nhận traffic từ lớp ingress này.

## Hệ quả
- (+) Học chuẩn đang được dùng cho dự án mới, không phụ thuộc thành phần đã ngừng bảo trì.
- (−) Ít tài liệu và ví dụ hơn ingress-nginx; một số annotation quen thuộc không có tương đương trực tiếp.

Liên quan: `overview.md` §13.1, §13.4, §19.4.
