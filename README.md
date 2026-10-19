# AI Team: bộ Claude Code agents khởi đầu

Bộ khởi đầu gồm 7 agent cấp senior, copy vào từng dự án. Sau khi copy, agent thuộc về dự án đó: bổ sung `CLAUDE.md`, skill riêng, và sửa thẳng agent để chuyên sâu cho dự án nếu cần.

| Agent | Vai trò | Đầu ra chính |
|---|---|---|
| `leader` | Tech Lead: lập plan, phân công, ADR, API contract, review cuối | `docs/plans/`, `docs/adr/`, `docs/api/` |
| `ba` | Business Analyst: user story, AC, business rule | `docs/requirements/` |
| `designer` | Product Designer UX/UI: flow, wireframe, a11y, handoff | `docs/design/` |
| `dev-fe` | Frontend Engineer: giao diện, state, tích hợp API, a11y, test | code + test |
| `dev-be` | Backend Engineer: API, business logic, data, migration, test | code + test |
| `tester` | QA/SDET: test case, test tự động, bug report | `docs/test/` + test |
| `devops` | DevOps/SRE: CI/CD, IaC, container, observability | `docs/ops/` + infra code |

| Skill | Nội dung | Nạp sẵn cho | Gọi khi cần |
|---|---|---|---|
| `team-workflow` | Điều phối pipeline giữa các agent | | Session chính |
| `brainstorm` | Hỏi đáp với người dùng để làm rõ yêu cầu mơ hồ, chốt thành brief | | Session chính |
| `solution-options` | Đưa ra và so sánh phương án kỹ thuật | | leader |
| `code-review` | Review code, tự review, nhận review | dev-fe, dev-be | leader (review cuối) |
| `ui-ux` | Bộ rule UX/UI có ID và checklist trước bàn giao | designer, dev-fe | tester, leader (khi có UI) |
| `verification` | Có bằng chứng đúng loại (AC, BR, contract, red–green) mới được báo xong | dev-fe, dev-be, tester, devops | |
| `systematic-debugging` | Tìm nguyên nhân gốc trước khi sửa, luật dừng sau 3 lần | | dev-fe, dev-be, tester (khi có bug) |

**Nạp sẵn** (field `skills:` trong frontmatter) là toàn bộ `SKILL.md` được nạp mỗi lần agent chạy. Chỉ dùng cho skill mà agent cần ở hầu hết task. **Gọi khi cần** thì chỉ tốn token khi thật sự dùng; agent biết lúc nào cần gọi nhờ dòng kích hoạt trong nội dung agent. Trong frontmatter, skill gọi khi cần được ghi thành dòng comment ngay trong danh sách `skills:`:

```yaml
skills:
  - code-review
  - verification
#  - systematic-debugging (khi sửa bug)
```

Agent không nạp sẵn skill nào thì comment cả dòng `skills:` (xem `leader.md`).

## Trong một dự án sau khi copy

| File | Chứa gì |
|---|---|
| `<project>/.claude/agents/*.md` | Vai trò, quy trình, tiêu chuẩn senior; sửa thêm cho chuyên sâu theo dự án |
| `<project>/CLAUDE.md` | Stack, lệnh, convention, môi trường |
| `<project>/.claude/skills/` | Domain, coding convention, runbook... |

Agent nào cũng bắt đầu bằng việc nắm bối cảnh: đọc `CLAUDE.md`, gọi skill liên quan và đọc code hiện có.

## Đưa vào một dự án

Copy vào source code của dự án và commit cùng code, để cả team dùng chung một phiên bản:

| Copy từ repo này | Vào dự án |
|---|---|
| `.claude/agents/*.md` | `<project>/.claude/agents/` |
| `.claude/skills/*` | `<project>/.claude/skills/` (bỏ `ui-ux` nếu dự án không có giao diện) |
| `templates/CLAUDE.project.md` | `<project>/CLAUDE.md` (gộp vào nếu dự án đã có) |

Không copy `CLAUDE.md` và `README.md` của repo này; hai file đó chỉ dùng cho repo `ai-team`.

Sau khi copy:

1. Điền `<project>/CLAUDE.md`, đặc biệt là **lệnh test/build** và **những việc agent không được tự làm**.
2. Thêm skill riêng vào `<project>/.claude/skills/<tên>/SKILL.md`, rồi gắn vào agent dùng nó: agent cần ở hầu hết task thì khai báo trong `skills:`; chỉ cần theo tình huống thì thêm thành dòng comment `#  - <skill> (khi nào)` trong danh sách `skills:`, kèm dòng kích hoạt ở đúng bước trong nội dung agent. Gợi ý:
   - `domain-knowledge`: thuật ngữ và quy tắc nghiệp vụ (cho ba, tester)
   - `fe-conventions`: stack frontend, cấu trúc, pattern component/state, guideline theo framework (cho dev-fe)
   - `be-conventions`: stack backend, phân lớp, error handling, migration (cho dev-be)
   - `design-system`: token, component, brand (cho designer, dev-fe)
   - `testing-strategy`: framework, fixture, test data (cho tester)
   - `deploy-runbook`: môi trường, quy trình release/rollback (cho devops)

## Cách dùng

```text
# Cả pipeline
/team-workflow Thêm tính năng đăng nhập bằng Google

# Gọi trực tiếp một agent
Dùng agent ba phân tích yêu cầu: ...
@agent-tester tái hiện bug: ...
```

## Lưu ý thiết kế
- **Subagent không gọi được subagent khác.** Session chính đóng vai orchestrator (qua skill `team-workflow`), còn `leader` lập kế hoạch và review.
- Các agent trao đổi qua **file artifact trong `docs/`**, không chép nội dung dài vào prompt.
- `model: inherit`: agent dùng model của session chính. Muốn cố định (ví dụ `opus` cho leader, dev-fe, dev-be) thì sửa frontmatter.
- Feature có cả FE và BE: `leader` chốt API contract (`docs/api/`) trước, rồi `dev-fe` và `dev-be` làm song song.
- `ba` bị giới hạn tool (không có Bash, không có MCP). Các agent còn lại kế thừa toàn bộ tool, kể cả MCP (Playwright, Figma...).
- Các hành động nguy hiểm (commit/push, deploy, `terraform apply`, migration DB thật) luôn cần người dùng xác nhận.
