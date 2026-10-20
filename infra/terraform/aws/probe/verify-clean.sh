#!/usr/bin/env bash
# Kiểm tra không còn tài nguyên AWS nào sau lab (overview.md §23.4 lớp 4). Chỉ ĐỌC, không xoá gì.
# Thoát 0 khi sạch; thoát 1 khi còn tài nguyên hoặc không kiểm được mục nào.
#
#   ./verify-clean.sh
#
# Cần `aws sso login` trước. Chạy lại vài phút sau destroy nếu thấy instance ở trạng thái shutting-down.
set -uo pipefail

region="${AWS_REGION:-ap-southeast-1}"
dirty=0
unknown=0

# check <tên> <lệnh aws in ra một số>
check() {
  local name="$1"
  shift
  local count
  if ! count="$("$@" 2>/dev/null)"; then
    printf '  %-34s KHÔNG KIỂM ĐƯỢC (thiếu quyền hoặc dịch vụ bị chặn)\n' "$name"
    unknown=1
    return
  fi
  count="${count//[$'\t\r\n ']/}"
  if [[ "$count" == "0" || "$count" == "None" || -z "$count" ]]; then
    printf '  %-34s sạch\n' "$name"
  else
    printf '  %-34s CÒN %s\n' "$name" "$count"
    dirty=1
  fi
}

echo "Region: $region"
echo "== Bắt buộc theo §23.4 =="
check "EC2 instance" aws ec2 describe-instances --region "$region" \
  --filters Name=instance-state-name,Values=pending,running,stopping,stopped,shutting-down \
  --query 'length(Reservations[].Instances[])' --output text
check "EBS volume" aws ec2 describe-volumes --region "$region" --query 'length(Volumes)' --output text
check "EBS snapshot (của mình)" aws ec2 describe-snapshots --region "$region" --owner-ids self \
  --query 'length(Snapshots)' --output text
check "Elastic IP" aws ec2 describe-addresses --region "$region" --query 'length(Addresses)' --output text
check "Load Balancer (ALB/NLB)" aws elbv2 describe-load-balancers --region "$region" \
  --query 'length(LoadBalancers)' --output text
check "NAT Gateway" aws ec2 describe-nat-gateways --region "$region" \
  --filter Name=state,Values=pending,available,deleting --query 'length(NatGateways)' --output text
check "RDS instance" aws rds describe-db-instances --region "$region" --query 'length(DBInstances)' --output text
check "RDS snapshot (thủ công)" aws rds describe-db-snapshots --region "$region" --snapshot-type manual \
  --query 'length(DBSnapshots)' --output text

echo "== Thêm cho các dịch vụ thăm dò =="
check "Spot request đang mở" aws ec2 describe-spot-instance-requests --region "$region" \
  --filters Name=state,Values=open,active --query 'length(SpotInstanceRequests)' --output text
check "Auto Scaling Group" aws autoscaling describe-auto-scaling-groups --region "$region" \
  --query 'length(AutoScalingGroups)' --output text
check "Target group" aws elbv2 describe-target-groups --region "$region" --query 'length(TargetGroups)' --output text
check "CloudFront distribution" aws cloudfront list-distributions \
  --query 'length(DistributionList.Items || `[]`)' --output text
check "S3 bucket tickethub-probe-*" aws s3api list-buckets \
  --query 'length(Buckets[?starts_with(Name, `tickethub-probe`)])' --output text
check "SES identity" aws sesv2 list-email-identities --region "$region" \
  --query 'length(EmailIdentities)' --output text
check "SNS topic tickethub-probe-*" aws sns list-topics --region "$region" \
  --query 'length(Topics[?contains(TopicArn, `tickethub-probe`)])' --output text
check "CloudWatch alarm tickethub-probe-*" aws cloudwatch describe-alarms --region "$region" \
  --alarm-name-prefix tickethub-probe --query 'length(MetricAlarms)' --output text
check "CloudWatch log group /tickethub" aws logs describe-log-groups --region "$region" \
  --log-group-name-prefix /tickethub --query 'length(logGroups)' --output text

echo
if [[ $dirty -eq 1 ]]; then
  echo "CHƯA SẠCH: còn tài nguyên ở trên. Gỡ hết rồi chạy lại." >&2
  exit 1
fi
if [[ $unknown -eq 1 ]]; then
  echo "Sạch ở các mục kiểm được, nhưng có mục không kiểm được: kiểm tay trên console / Cost Explorer." >&2
  exit 1
fi
echo "SẠCH."
