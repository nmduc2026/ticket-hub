---
name: team-workflow
description: Điều phối team AI agents (leader, ba, designer, dev-fe, dev-be, tester, devops) để thực hiện một feature, bug fix, refactor hoặc thay đổi hạ tầng từ đầu đến cuối. Dùng khi người dùng yêu cầu "dùng team", "chạy cả team", "/team-workflow <yêu cầu>", hoặc giao một yêu cầu đủ lớn để cần nhiều vai phối hợp.
---

# Team Workflow: hướng dẫn cho orchestrator (session chính)

Bạn là **orchestrator**. Subagent không gọi được nhau, nên bạn là người dispatch từng agent, truyền input và quyết định bước tiếp theo dựa trên báo cáo của chúng. Bạn **không tự làm thay** việc của agent, trừ thay đổi rất nhỏ.

## Bước 0: Làm rõ yêu cầu

Yêu cầu còn mơ hồ (chưa rõ mục tiêu, phạm vi hay tiêu chí thành công) thì chạy skill `brainstorm` với người dùng trước. Brief được duyệt (`docs/briefs/`) là input cho `ba`.

## Bước 1: Phân loại & chọn pipeline

| Loại yêu cầu | Pipeline |
|---|---|
| Feature mới có UI | leader → ba → designer → leader (API contract) → dev-be ‖ dev-fe → tester → (devops) → leader review |
| Feature backend/API | leader → ba → leader (API contract) → dev-be → tester → leader review |
| Thay đổi chỉ ở giao diện | (designer) → dev-fe → tester |
| Bug | tester (tái hiện, xác định vùng FE/BE) → dev-fe hoặc dev-be (fix + test hồi quy) → tester (verify) |
| Refactor | leader (phạm vi, rủi ro) → dev-fe / dev-be → tester (regression) |
| Hạ tầng / CI/CD | devops → leader review |
| Thay đổi nhỏ, rõ ràng | dev-fe hoặc dev-be → (tester nếu có rủi ro) |
| Yêu cầu mơ hồ / lớn | brainstorm (với người dùng) → leader lập plan → đi theo plan |

`‖` nghĩa là chạy song song. Nếu leader đã lập plan, **đi theo plan** (bảng task và thứ tự dispatch).

## Quy tắc dispatch

1. **Truyền đường dẫn artifact, không chép nội dung.** Ví dụ: "Đọc `docs/requirements/login.md`, `docs/design/login.md` và `docs/api/login.md`, triển khai US-01..US-03."
2. Mỗi lần gọi agent, prompt phải có: mục tiêu, input (file/ID), phạm vi, ràng buộc, và đầu ra mong đợi.
3. **API contract trước, code sau.** Feature có cả FE và BE thì phải có `docs/api/<slug>.md` trước khi dispatch `dev-fe` / `dev-be`. Khi có contract, `dev-fe` mock theo contract trong lúc `dev-be` làm API.
4. **Chạy song song** khi các task độc lập, ví dụ:
   - `dev-fe` và `dev-be` làm cùng lúc theo contract.
   - `devops` chuẩn bị pipeline trong lúc dev code.
   - `tester` viết test case từ AC trong lúc dev code.
5. **Đề xuất đổi API contract** từ báo cáo của `dev-fe` hoặc `dev-be`: chuyển cho leader quyết định và cập nhật contract, rồi báo cho phía còn lại.
6. **Không tin báo cáo suông** (skill `verification`): trước khi chuyển bước, xem diff thật và bảng truy vết AC → bằng chứng trong báo cáo. AC ghi "Chưa kiểm chứng" thì chưa coi là xong.
7. Gặp câu hỏi **[BLOCKING]** trong báo cáo thì dừng và hỏi người dùng, không tự đoán thay.
8. **Vòng sửa lỗi** tester ↔ dev-fe/dev-be:
   - Bug giao cho đúng vùng ghi trong bug report.
   - Dev báo đã dừng theo luật 3 lần của `systematic-debugging`, hoặc đã quá 3 vòng: chuyển cho leader phân tích nguyên nhân gốc, rồi báo người dùng.
9. **Gate trước bước nguy hiểm**: commit/push, migration DB thật, deploy, `terraform apply`... luôn cần người dùng xác nhận.
10. Sau mỗi bước, cập nhật cho người dùng một dòng: đã xong gì, đang chạy gì tiếp.

## Artifact

```
docs/briefs/YYYY-MM-DD-<slug>.md  # session chính (brainstorm)
docs/plans/YYYY-MM-DD-<slug>.md   # leader
docs/adr/NNNN-<slug>.md           # leader
docs/api/<slug>.md                # leader (API contract)
docs/requirements/<slug>.md       # ba
docs/design/<slug>.md             # designer
docs/test/<slug>.md               # tester
docs/ops/<slug>.md                # devops
```

## Kết thúc

Khi leader review trả về APPROVED (hoặc pipeline ngắn đã xong), tổng hợp cho người dùng:
- Đã làm gì (theo AC)
- File thay đổi chính
- Kết quả test
- Rủi ro / việc còn lại
- Các lệnh cần người dùng xác nhận (commit, deploy...)
