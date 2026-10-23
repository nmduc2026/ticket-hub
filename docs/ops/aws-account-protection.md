# Bảo vệ tài khoản AWS (lớp 1 và 2)

Nguồn: `overview.md` §23.4. Làm **trước** khi chạy bất kỳ Terraform nào ở `infra/terraform/aws/`. Rủi ro lớn nhất là lộ khoá rồi bị đào coin; lớp 2 giới hạn thiệt hại nếu lớp 1 hỏng.

Mọi bước dưới đây do người dùng làm trên console AWS (cần quyền root hoặc admin). Không lưu mật khẩu, mã MFA hay khoá vào repo.

## Lớp 1: bảo mật tài khoản

| # | Việc | Cách làm | Xong khi |
|---|---|---|---|
| 1.1 | MFA cho root | Console → tên tài khoản → Security credentials → Assign MFA (ứng dụng xác thực hoặc passkey). Ghi mã khôi phục ở nơi ngoài máy dev | Đăng nhập root đòi mã MFA |
| 1.2 | Không dùng root hằng ngày | Sau bước 1.3 – 1.5 cất mật khẩu root, không tạo access key cho root | Security credentials của root không có access key |
| 1.3 | Bật IAM Identity Center | Console → IAM Identity Center → Enable (region `ap-southeast-1`) | Có portal URL dạng `https://<id>.awsapps.com/start` |
| 1.4 | Tạo user và quyền | Identity Center → Users → Add user (email của bạn); Permission sets: `AdministratorAccess` thời hạn phiên 4 giờ; gán user vào tài khoản | User đăng nhập được portal |
| 1.5 | MFA cho user Identity Center | Settings → Authentication → MFA: bắt buộc mỗi lần đăng nhập | Portal đòi MFA |
| 1.6 | Không tạo access key dài hạn | Chỉ dùng `aws sso login` (credentials tạm). Cấu hình: `aws configure sso` (profile `tickethub`, region `ap-southeast-1`) | `aws sts get-caller-identity --profile tickethub` chạy được và IAM không có user / access key nào |
| 1.7 | GitHub Actions dùng OIDC | Làm ở lab cần CI truy cập AWS (Sprint 2): IAM Identity Provider `token.actions.githubusercontent.com` + role giới hạn theo repo. Không lưu access key trong GitHub Secrets | Sẽ ghi lại ở plan Sprint 2 |
| 1.8 | gitleaks chặn secret | Đã có: `git config core.hooksPath .githooks` và job `secrets` trong CI | Commit thử chứa khoá giả bị chặn |

Kiểm nhanh không còn access key: `aws iam list-users` rỗng và `aws iam get-account-summary --query 'SummaryMap.AccountAccessKeysPresent'` bằng `0` (root không có key).

## Lớp 2: cảnh báo và tự động chặn

Vào Billing and Cost Management → Budgets. Email nhận cảnh báo: email của bạn; kiểm hộp thư rác sau khi tạo.

| # | Việc | Cấu hình | Xong khi |
|---|---|---|---|
| 2.1 | Zero-spend budget | Template "Zero spend budget": cảnh báo khi chi phí > 0,01$ | Budget ở trạng thái OK, email xác nhận đã nhận |
| 2.2 | Monthly cost budget | Hạn mức 50$/tháng; ngưỡng cảnh báo 5$ (thực tế), 20$ (thực tế), 50$ (thực tế) và 50$ (dự báo) | 4 cảnh báo hiển thị |
| 2.3 | Budget Actions (ở ngưỡng 50$) | Hành động 1: IAM policy chặn tạo tài nguyên mới (deny `ec2:RunInstances`, `rds:CreateDBInstance`, `elasticloadbalancing:Create*`…) gắn vào permission set / role; hành động 2: stop EC2 / RDS theo tag `Project=tickethub`. Chọn "tự động chạy". Cần IAM role cho Budgets | Action có trạng thái "Ready" |
| 2.4 | Cost Anomaly Detection | Cost Management → Cost Anomaly Detection → Create monitor (AWS services), subscription gửi email khi bất thường > 5$ | Monitor Active |
| 2.5 | Free Tier usage alerts | Billing preferences → bật "Receive Free Tier Usage Alerts" và "Receive Billing Alerts" | Hai ô đã bật |

CLI tương đương cho 2.1 (không bắt buộc):

```bash
aws budgets create-budget --account-id "$(aws sts get-caller-identity --query Account --output text)" \
  --budget '{"BudgetName":"zero-spend","BudgetLimit":{"Amount":"0.01","Unit":"USD"},"TimeUnit":"MONTHLY","BudgetType":"COST"}' \
  --notifications-with-subscribers '[{"Notification":{"NotificationType":"ACTUAL","ComparisonOperator":"GREATER_THAN","Threshold":100,"ThresholdType":"PERCENTAGE"},"Subscribers":[{"SubscriptionType":"EMAIL","Address":"EMAIL_CUA_BAN"}]}]'
```

## Ghi nhận khi xong

Điền ngày làm và người xác nhận vào bảng dưới (người dùng tự điền; không chép mã hay khoá).

| Mục | Ngày xong | Ghi chú |
|---|---|---|
| 1.1 – 1.6 | | |
| 2.1 – 2.5 | | |
