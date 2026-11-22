#!/usr/bin/env python3
"""Validate workflow YAML and the repository's branch/deployment trigger matrix."""

from pathlib import Path
import os
import re
import subprocess
import sys
import textwrap
import json

import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"


def load(path):
    # BaseLoader preserves GitHub's `on` key as a string instead of YAML 1.1 bool.
    value = yaml.load(path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    if not isinstance(value, dict) or not isinstance(value.get("on"), dict):
        raise AssertionError(f"{path.name}: expected a workflow mapping with an on trigger")
    return value


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    workflows = {path.name: load(path) for path in sorted(WORKFLOWS.glob("*.yml"))}
    deploy = workflows["deploy.yml"]
    require(set(deploy["on"]) == {"workflow_dispatch"}, "web/backend deployment must be manual-dispatch only")
    deploy_job = deploy["jobs"]["build-and-deploy"]
    deploy_gate = deploy_job["if"]
    for required in ("equar/bingo-platform", "workflow_dispatch", "refs/heads/develop", "refs/heads/main", "confirm_production"):
        require(required in deploy_gate, f"deploy job gate is missing {required}")
    require("production" in deploy_job["environment"]["name"] and "test" in deploy_job["environment"]["name"], "deploy must map to test/production environments")

    ci = workflows["pr-validation-pipeline.yml"]
    require(set(ci["on"]["pull_request"]["branches"]) >= {"develop", "release", "main"}, "CI must validate PRs to all promotion branches")

    admin_e2e = workflows["admin-browser-e2e.yml"]
    require(set(admin_e2e["on"]) == {"workflow_dispatch"}, "Admin browser E2E must be manually dispatched")
    admin_e2e_job = admin_e2e["jobs"]["chromium"]
    for required in ("equar/bingo-platform", "refs/heads/main", "ADMIN_E2E_ENABLED"):
        require(required in admin_e2e_job["if"], f"Admin browser E2E gate is missing {required}")
    require(admin_e2e_job["environment"] == "admin-e2e", "Admin browser E2E must use its protected disposable environment")
    require("playwright install --with-deps chromium" in str(admin_e2e_job), "Admin browser E2E must install Chromium")

    guard = workflows["branch-flow-guard.yml"]
    require(set(guard["on"]) == {"pull_request"}, "branch policy must run only as a pull_request check")
    guard_text = (WORKFLOWS / "branch-flow-guard.yml").read_text(encoding="utf-8")
    node_block = re.search(r"node <<'NODE'\n(.*?)\n\s+NODE", guard_text, re.DOTALL)
    require(node_block is not None, "branch guard must keep its executable policy cases")
    env = os.environ | {"BRANCH_POLICY_TESTS_ONLY": "1"}
    subprocess.run(["node", "-e", textwrap.dedent(node_block.group(1))], cwd=ROOT, env=env, check=True)

    production_workflows = {
        "bingo-player-android-production.yml": "bingo-app-android-production",
        "bingo-player-ios-app-store.yml": "bingo-app-ios-production",
    }
    for name, environment in production_workflows.items():
        workflow = workflows[name]
        require(set(workflow["on"]) == {"workflow_dispatch"}, f"{name}: production publication must be manually dispatched")
        job = next(iter(workflow["jobs"].values()))
        for required in ("equar/bingo-platform", "workflow_dispatch", "refs/heads/main", "confirm_production"):
            require(required in job["if"], f"{name}: production job gate is missing {required}")
        require(job["environment"] == environment, f"{name}: expected protected environment {environment}")
        require("--source-branch main" in str(job), f"{name}: provenance must be verified against main")

    for name in ("bingo-player-android-internal-testing.yml", "bingo-player-ios-testflight.yml"):
        workflow = workflows[name]
        require(set(workflow["on"]) == {"workflow_dispatch"}, f"{name}: store upload must not run on pushes or pull requests")
        job = next(iter(workflow["jobs"].values()))
        require("equar/bingo-platform" in job["if"] and "refs/heads/main" in job["if"], f"{name}: secret-bearing upload must run only from main in the canonical repository")
        expected_environment = {
            "bingo-player-android-internal-testing.yml": "bingo-app-android-internal",
            "bingo-player-ios-testflight.yml": "bingo-app-ios-testflight",
        }[name]
        require(job.get("environment") == expected_environment, f"{name}: signing credentials must be scoped to {expected_environment}")

    for name, workflow in workflows.items():
        require("pull_request_target" not in workflow["on"], f"{name}: pull_request_target is not allowed")

    launch = json.loads((ROOT / ".vscode" / "launch.json").read_text(encoding="utf-8"))
    configurations = launch["configurations"]
    require({entry["name"] for entry in configurations} == {
        "Bingo Player — Android emulator", "Bingo Player — Chrome", "Bingo Player — Windows"
    }, "VS Code must expose only the supported Player Android, Chrome, and Windows targets")
    require(all("bingo-player" in entry["program"] and "bingo-player" in entry["cwd"] for entry in configurations),
            "every VS Code Flutter launch target must resolve to bingo-player")
    by_name = {entry["name"]: entry for entry in configurations}
    require("--web-port=5180" in by_name["Bingo Player — Chrome"]["toolArgs"],
            "Player Chrome launch must retain its configured development port")
    require("deviceId" not in by_name["Bingo Player — Android emulator"]
            and by_name["Bingo Player — Windows"]["deviceId"] == "windows",
            "Player Android emulator and Windows launches must remain selectable")

    component_runner = (ROOT / "scripts/ci/run-component-checks.mjs").read_text(encoding="utf-8")
    hook = (ROOT / "scripts/hooks/Invoke-BingoGitChecks.ps1").read_text(encoding="utf-8")
    package_builder = (ROOT / "deployment/mobile/build-packages.sh").read_text(encoding="utf-8")
    local_launcher = (ROOT / "scripts/Run-BingoLocal.ps1").read_text(encoding="utf-8")
    signing_helper = (ROOT / "deployment/mobile/prepare-android-signing.py").read_text(encoding="utf-8")
    package_verifier = (ROOT / "deployment/mobile/verify-android-package.py").read_text(encoding="utf-8")
    device_installer = (ROOT / "deployment/mobile/install-virtual-device.py").read_text(encoding="utf-8")
    client_tooling = "\n".join((component_runner, hook, package_builder, local_launcher,
                                signing_helper, package_verifier, device_installer))
    require("bingo-agent" not in client_tooling,
            "local CI, Git hooks, and mobile package builds must not target the deleted Agent Flutter app")
    require("deployment\\/terraform\\/" in component_runner and "deployment/(aws|lightsail|terraform)" in hook,
            "Terraform changes must map to infrastructure validation in CI and local hooks")
    require("validate-deployment-infrastructure" in hook,
            "the infrastructure workflow must map to infrastructure checks in local hooks")

    print(f"Validated YAML for {len(workflows)} workflows, promotion policy, Player launch targets, and infrastructure mappings.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, yaml.YAMLError) as error:
        print(f"Workflow validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
