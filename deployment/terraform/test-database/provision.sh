#!/usr/bin/env bash
set -euo pipefail

action="${1:-apply}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
vars="$root/test.tfvars"

if [[ "$action" != "plan" && "$action" != "apply" && "$action" != "destroy" ]]; then
  echo "Usage: $0 [plan|apply|destroy]" >&2
  exit 64
fi
command -v terraform >/dev/null || { echo "Terraform is required." >&2; exit 1; }
[[ -f "$vars" ]] || {
  echo "Create $vars from $root/test.tfvars.example and set TF_VAR_mysql_admin_password." >&2
  exit 1
}

terraform -chdir="$root" init
if [[ "$action" == "plan" ]]; then
  terraform -chdir="$root" plan -var-file="$vars"
  exit 0
fi

read -r -p "This will $action the bingotest schema on the existing AWS test MySQL server. Type bingotest-$action to continue: " confirmation
[[ "$confirmation" == "bingotest-$action" ]] || { echo "Cancelled." >&2; exit 1; }
terraform -chdir="$root" "$action" -var-file="$vars"
