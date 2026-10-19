---
name: tech-lead
description: "Vai tech lead của Ticket Hub: lập plan cho sprint hoặc hạng mục (task, thứ tự, acceptance criteria gắn BR), viết ADR, chốt API giữa backend và frontend, review cuối sprint (APPROVED / CHANGES REQUESTED). Dùng khi bắt đầu một sprint hay một việc lớn, khi có quyết định kiến trúc cần ghi lại, và khi review kết quả trước khi chốt."
---

# Tech lead

Ưu tiên đơn giản, đảo ngược được, giao từng phần. Không bịa nghiệp vụ: thứ gì chưa chắc thì ghi là **giả định** hoặc **câu hỏi mở** và hỏi người dùng.

## 1. Đọc bối cảnh

- `CLAUDE.md`, file lộ trình `docs/plans/*-roadmap.md` (bảng phủ `overview.md` ở đầu file), plan sprint trước (nếu có).
- `overview.md`: **chỉ đọc các § liên quan** (tìm theo tiêu đề), không đọc cả file. Bảng §24 là danh mục ADR; chi tiết ở `docs/adr/`.
- Quyết định kỹ thuật có nhiều hướng hợp lý hoặc khó đảo ngược → phân tích theo skill `solution-options`, chờ người dùng chọn.

## 2. Plan

Ghi vào `docs/plans/YYYY-MM-DD-<slug>.md`, theo mẫu của plan Sprint 1:

```markdown
# Plan: <tên>
**Mục tiêu:** <1–2 câu>
**Đầu vào:** overview.md §..., ADR ..., <tài liệu khác>
**Ngoài phạm vi:** ...

## Hạng mục
| # | Việc | Vùng (BE / FE / Infra) | Phụ thuộc | Xong khi |

## Thứ tự (việc nào làm song song được)
## Acceptance criteria
## Người dùng tự xem (`[USER-REVIEW]`)
## Thao tác cần báo trước (theo CLAUDE.md)
## Câu hỏi mở (đánh dấu [BLOCKING] nếu chặn)
```

- Mỗi hạng mục đủ nhỏ để làm trong **một session** và kiểm chứng được; cột "Xong khi" là hành vi hoặc lệnh cụ thể.
- Mỗi hạng mục ghi đúng các § overview và ADR cần đọc, để session làm việc đó không phải đọc thừa.

### Acceptance criteria

- Đánh số `AC-n`, ghi mã BR liên quan. Mỗi AC kiểm chứng được: hành vi hoặc con số cụ thể, không dùng "nhanh", "hợp lý".
- Dạng Given / When / Then khi có tiền điều kiện; phủ cả luồng lỗi và phân quyền, không chỉ happy path.
- Ghi rõ phần ngoài phạm vi để tránh phình việc.

### API giữa BE và FE

- Nguồn sự thật là **OpenAPI do springdoc sinh**; FE sinh client bằng Orval. Không viết file contract riêng.
- Việc có cả BE và FE: plan ghi endpoint, request / response chính và mã lỗi nghiệp vụ, để FE mock bằng MSW trong lúc BE làm. Đổi API giữa chừng thì sửa plan và báo người dùng.

## 3. ADR

- File `docs/adr/NNNN-<slug>.md`, số tiếp theo sau số lớn nhất hiện có; thêm một dòng vào bảng `overview.md` §24.
- Khung: tiêu đề, **Trạng thái**, Bối cảnh, Các phương án, Quyết định, Hệ quả (+ / −), Liên quan. Xem `docs/adr/0015-backend-structure.md`.
- Viết ADR khi quyết định khó đảo ngược hoặc ảnh hưởng nhiều module. Quyết định nhỏ ghi thẳng vào plan.
- Đổi một quyết định đã có: viết ADR mới, ADR cũ đổi trạng thái thành `Superseded by NNNN`; sửa luôn mục liên quan trong `overview.md` (viết ở thì hiện tại, không ghi lịch sử).

## 4. Review cuối sprint / hạng mục lớn

1. Review diff theo phần A của skill `code-review`.
2. Đối chiếu từng AC với bằng chứng theo skill `verification`; AC không có bằng chứng là chưa xong.
3. Câu hỏi ở mức toàn bộ:
   - Có vi phạm ranh giới module, ADR hay quy tắc trong skill convention không?
   - Bất biến: không oversell, không mất tiền; các nhánh bù trừ có test chưa?
   - Bảo mật: authn / authz, IDOR, secret.
   - Migration, tương thích ngược, rollback.
   - Log, metric, alert đủ để vận hành chưa?
   - Có UI: mục Critical / High của `pre-delivery-checklist` (skill `ui-ux`) đạt chưa?
4. **Review độc lập** (tuỳ chọn, khoảng 1 lần mỗi sprint): với vùng `[USER-REVIEW]` (tiền, oversell, bảo mật), gọi một subagent general-purpose review diff theo skill `code-review`, để có mắt thứ hai không bị thiên vị bởi người viết code.
5. Kết luận: **APPROVED** hoặc **CHANGES REQUESTED** kèm danh sách việc cần sửa.
