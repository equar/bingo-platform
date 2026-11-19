#!/usr/bin/env bash
set -euo pipefail

environment="${1:-}"
action="${2:-apply}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$environment" != "prod" && "$environment" != "test" ]]; then
  echo "Usage: $0 {prod|test} [plan|apply|destroy]" >&2
  exit 64
fi
if [[ "$action" != "plan" && "$action" != "apply" && "$action" != "destroy" ]]; then
  echo "Action must be plan, apply, or destroy." >&2
  exit 64
fi
command -v terraform >/dev/null || { echo "Terraform is required." >&2; exit 1; }

vars="$root/env/$environment.tfvars"
if [[ ! -f "$vars" ]]; then
  echo "Create $vars from $vars.example and provide secrets through TF_VAR_database_admin_password." >&2
  exit 1
fi

cd "$root"
terraform init
terraform workspace select "$environment" 2>/dev/null || terraform workspace new "$environment"

if [[ "$action" == "plan" ]]; then
  terraform plan -var-file="$vars"
  exit 0
fi

if [[ "$environment" == "prod" ]]; then
  read -r -p "Production $action changes real AWS resources. Type prod-$action to continue: " confirmation
  [[ "$confirmation" == "prod-$action" ]] || { echo "Cancelled." >&2; exit 1; }
else
  read -r -p "Test $action changes AWS test resources. Type test-$action to continue: " confirmation
  [[ "$confirmation" == "test-$action" ]] || { echo "Cancelled." >&2; exit 1; }
fi

terraform "$action" -var-file="$vars"
