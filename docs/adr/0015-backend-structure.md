# 0015. Cấu trúc backend: Maven multi-module `app` + `gateway`

**Trạng thái:** Accepted

## Bối cảnh
Xác thực SPA dựa vào BFF (ADR 0003), tức Gateway là một ứng dụng riêng. Phần nghiệp vụ là modular monolith (ADR 0012).

## Các phương án
- **Multi-module ngay từ đầu:** `backend/pom.xml` (parent) + `backend/app/` + `backend/gateway/`.
- **Một Maven module, thêm Gateway sau:** khởi động nhanh hơn, nhưng frontend phải đăng nhập theo cách khác rồi viết lại khi có BFF.

## Quyết định
Multi-module ngay từ Sprint 1:

```
backend/
├── pom.xml        # parent, dependencyManagement
├── app/           # modular monolith, package gốc com.ndz
│                  #   account, catalog, booking, payment, ticket, notification, shared
│                  #   (+ queue khi làm phòng chờ ở Sprint 7)
└── gateway/       # Spring Cloud Gateway (BFF)
```

`queue` là module riêng, không gộp vào `booking`: khớp ranh giới service ở `overview.md` §5, và phòng chờ có lượng kết nối SSE lớn nên cần tách được sau này.

`platform/*`, `contracts/` và các `*-service` chỉ thêm khi tách service (Sprint 8).

## Hệ quả
- (+) Luồng đăng nhập làm đúng một lần, đúng thiết kế đích.
- (+) Hai ứng dụng có vòng đời và cấu hình riêng, giống môi trường thật.
- (−) Local phải chạy 2 ứng dụng; compose `core` và Helm chart phải có cả hai.

Liên quan: `overview.md` §22, ADR 0003, 0007, 0012.
