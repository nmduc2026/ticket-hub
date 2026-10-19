# ai-team

Repo này là **nguồn gốc (source of truth)** của bộ team AI agents dùng chung cho nhiều dự án. Bản thân nó không phải một dự án phần mềm.

## Cấu trúc
- `.claude/agents/*.md` — 7 agent: leader, ba, designer, dev-fe, dev-be, tester, devops
- `.claude/skills/` — 7 skill: team-workflow, brainstorm (session chính); solution-options, code-review, ui-ux, verification, systematic-debugging (agent)
- `templates/CLAUDE.project.md` — mẫu `CLAUDE.md` cho từng dự án

Cách dùng: copy `.claude/agents/` và `.claude/skills/` vào source code của dự án (xem README).

## Quy tắc khi chỉnh sửa agent
- Viết agent **như đã thuộc về một dự án**: không có câu kiểu "bạn được dùng chung cho nhiều dự án", "không giả định stack", "nếu dự án quy định khác thì...", và không liệt kê đủ loại stack/công cụ cho mọi trường hợp. Sau khi copy, dự án được phép sửa agent cho chuyên sâu.
- Không ghi stack, domain hay đường dẫn riêng của một dự án cụ thể vào repo này.
- Mỗi agent giữ cấu trúc: Nắm bối cảnh → Trách nhiệm → Quy trình → Nguyên tắc/Checklist → Báo cáo trả về.
- Đổi định dạng báo cáo hoặc đường dẫn artifact thì cập nhật đồng bộ cả `team-workflow/SKILL.md`.
- Gắn skill vào agent:
  - Skill agent cần ở **hầu hết** task: khai báo trong `skills:` (nạp sẵn, tốn token mỗi lần chạy).
  - Skill chỉ cần **theo tình huống**: không khai báo trong `skills:`. Ghi thành dòng comment `#  - <skill> (khi nào)` ngay trong danh sách `skills:` (agent không nạp sẵn skill nào thì comment cả dòng `# skills:`), và thêm dòng kích hoạt ở đúng bước trong nội dung agent.
  - Cập nhật bảng skill trong README cho khớp.
- Đổi ID rule trong `ui-ux` thì cập nhật cả `pre-delivery-checklist.md` và các agent có nhắc tới ID đó.
- `description` có dấu `: ` thì phải bọc trong dấu nháy kép, nếu không YAML sẽ báo lỗi.
- `description` trong frontmatter quyết định khi nào agent được tự gọi; viết cụ thể, có "dùng khi...".
