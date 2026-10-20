# Ticket Hub — frontend

SPA React 19 + TypeScript strict + Vite. Quy ước: `.claude/skills/ticket-hub-react-conventions`.

```bash
npm install            # cần npm 11 (npx -y npm@11 install nếu máy đang dùng npm 10: npm 10 lỗi khi phân giải peer dependency của Vite 8)
npm run dev            # :5173, chuyển tiếp /api và luồng đăng nhập tới Gateway :8080
npm run api:generate   # sinh client từ openapi/openapi.json (không sửa tay src/api/generated)
npm run typecheck && npm run lint && npm test
```

- `openapi/openapi.json` do backend ghi ra khi chạy `./mvnw verify` (OpenApiContractTests); đổi API thì commit file này cùng thay đổi.
- Dev cần chạy: compose profile `core`, `app` (:8081), `gateway` (:8080), rồi `npm run dev`.
- Orval 7.13 (Orval 8 cần Node ≥ 22.18). Hiện sinh Zod chưa ra file vì spec mới có `GET`; kiểm lại khi có endpoint nhận body.
