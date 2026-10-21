#!/usr/bin/env bash
# Build frontend rồi đẩy lên S3 của lab 1 và làm mới CloudFront. Chạy SAU `terraform apply`.
#
#   ./deploy-frontend.sh
#
# Cần `aws sso login`. Đường dẫn tương đối nên chạy ở bất cứ đâu.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
frontend="$here/../../../../frontend"

bucket="$(terraform -chdir="$here" output -raw frontend_bucket)"
distribution="$(terraform -chdir="$here" output -raw cloudfront_distribution_id)"

# Trang tài khoản Keycloak cùng domain (đi qua CloudFront /auth), không phải localhost:8180 của dev.
(cd "$frontend" && VITE_KEYCLOAK_ACCOUNT_URL=/auth/realms/tickethub/account npm run build)

# Tệp có hash trong tên cache lâu; index.html luôn tải mới.
aws s3 sync "$frontend/dist" "s3://$bucket" --delete --exclude index.html \
  --cache-control "public,max-age=31536000,immutable"
aws s3 cp "$frontend/dist/index.html" "s3://$bucket/index.html" --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id "$distribution" --paths "/index.html" >/dev/null

echo "Đã đẩy frontend. Mở: $(terraform -chdir="$here" output -raw public_url)"
