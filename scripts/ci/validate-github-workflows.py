#!/usr/bin/env python3
"""Validate workflow YAML and the repository's branch/deployment trigger matrix."""

from pathlib import Path
import os
import re
import subprocess
import sys
import textwrap

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

    for name, workflow in workflows.items():
        require("pull_request_target" not in workflow["on"], f"{name}: pull_request_target is not allowed")

    print(f"Validated YAML for {len(workflows)} workflows and promotion/deployment trigger policy.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, yaml.YAMLError) as error:
        print(f"Workflow validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
