#!/usr/bin/env bash
# Thăm dò MỘT dịch vụ AWS trên Free plan: plan -> (bạn đồng ý) apply -> ghi kết quả -> (bạn đồng ý) destroy -> verify-clean.
#
#   ./run-probe.sh ec2
#   ./run-probe.sh ses -var email=ban@example.com
#   ./run-probe.sh cloudwatch-sns -var email=ban@example.com
#
# Cần đăng nhập trước: `aws sso login` (không dùng access key dài hạn, overview.md §23.4 lớp 1).
# Script KHÔNG tự apply: luôn hỏi trước, và apply đúng bản plan vừa hiển thị.
set -uo pipefail

service="${1:-}"
if [[ -z "$service" ]]; then
  echo "Dùng: $0 <dịch vụ> [tham số terraform...]" >&2
  echo "Dịch vụ: $(cd "$(dirname "$0")" && ls -d */ | tr -d '/' | tr '\n' ' ')" >&2
  exit 2
fi
shift
extra=("$@")

root="$(cd "$(dirname "$0")" && pwd)"
dir="$root/$service"
results="$root/results"
if [[ ! -d "$dir" ]]; then
  echo "Không có dịch vụ '$service'" >&2
  exit 2
fi
mkdir -p "$results"

echo "== Tài khoản đang dùng =="
aws sts get-caller-identity --query '[Account,Arn]' --output text || {
  echo "Chưa đăng nhập AWS. Chạy: aws sso login" >&2
  exit 1
}

cd "$dir" || exit 1
terraform init -input=false > /dev/null || exit 1

echo
echo "== PLAN: $service =="
terraform plan -input=false "${extra[@]}" -out=tfplan || exit 1

read -r -p "Chạy APPLY thật trên AWS (region ap-southeast-1, có thể phát sinh chi phí nhỏ)? [y/N] " answer
if [[ "$answer" != "y" ]]; then
  echo "Đã dừng, chưa tạo gì."
  rm -f tfplan
  exit 0
fi

started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if terraform apply -input=false tfplan 2> apply.err; then
  status="DÙNG ĐƯỢC"
  detail="apply thành công"
else
  status="BỊ CHẶN / LỖI"
  detail="$(grep -E 'Error|error|Blocked|not (eligible|supported|allowed)|Free' apply.err | head -n 5 | tr '\n' ' ')"
  echo "--- Lỗi apply ---" >&2
  cat apply.err >&2
fi
rm -f tfplan

{
  echo "dịch vụ: $service"
  echo "thời điểm: $started"
  echo "kết quả: $status"
  echo "chi tiết: $detail"
} > "$results/$service.txt"
echo "Kết quả: $status (đã ghi $results/$service.txt). Chép vào docs/ops/aws-free-plan-probe.md."

echo
echo "Phải gỡ ngay: lab xong thì destroy, không stop (overview.md §23.4)."
read -r -p "Chạy DESTROY bây giờ? [Y/n] " destroy
if [[ "$destroy" == "n" || "$destroy" == "N" ]]; then
  echo "CHƯA gỡ. Nhớ chạy: (cd $dir && terraform destroy ${extra[*]})" >&2
  exit 0
fi
terraform destroy -input=false -auto-approve "${extra[@]}" || {
  echo "Destroy lỗi: chạy lại hoặc gỡ tay rồi chạy verify-clean.sh" >&2
  exit 1
}

echo
"$root/verify-clean.sh"
