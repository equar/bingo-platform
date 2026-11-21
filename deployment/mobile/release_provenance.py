#!/usr/bin/env python3
"""Write and verify provenance for mobile builds promoted to production."""

import argparse
import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit


PRODUCTION_API_URL = "https://api.bingo.bakaroo.com"
TEST_API_HOST = "api.test.bakaroo.com"


def normalize_api_base_url(value):
    parsed = urlsplit(value.strip())
    if not parsed.scheme or not parsed.hostname:
        raise ValueError("API base URL must be an absolute URL with a host.")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("API base URL must not contain credentials.")
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise ValueError("API base URL must be an origin without a path, query, or fragment.")
    try:
        port = parsed.port
    except ValueError as error:
        raise ValueError("API base URL contains an invalid port.") from error

    scheme = parsed.scheme.lower()
    hostname = parsed.hostname.lower().rstrip(".")
    if scheme not in ("https", "http"):
        raise ValueError("API base URL must use HTTPS, or HTTP for the exact test API host.")
    if scheme == "http" and hostname != TEST_API_HOST:
        raise ValueError("HTTP is allowed only for api.test.bakaroo.com.")
    if scheme == "http" and port not in (None, 80):
        raise ValueError("The HTTP test API must use its default port.")

    default_port = 443 if scheme == "https" else 80
    authority = hostname if port is None or port == default_port else f"{hostname}:{port}"
    return f"{scheme}://{authority}"


def read_build_number(pubspec_path):
    for line in Path(pubspec_path).read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"\s*version:\s*\S+\+([0-9]+)\s*(?:#.*)?", line)
        if match:
            return match.group(1)
    raise ValueError("pubspec.yaml must define version with a numeric +build-number suffix.")


def read_app_version(pubspec_path):
    for line in Path(pubspec_path).read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(
            r"\s*version:\s*([0-9]+(?:\.[0-9]+){1,2})\+[0-9]+\s*(?:#.*)?",
            line,
        )
        if match:
            return match.group(1)
    raise ValueError("pubspec.yaml must define a numeric app version and build number.")


def _required_environment(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"{name} is required to write release provenance.")
    return value


def write_manifest(args):
    build_number = str(args.build_number)
    if not build_number.isdecimal() or int(build_number) < 1:
        raise ValueError("Build number must be a positive integer.")
    application_id = args.application_id.strip()
    if not application_id:
        raise ValueError("Application ID is required.")
    app_version = args.app_version.strip()
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,2}", app_version):
        raise ValueError("App version must use numeric major.minor or major.minor.patch format.")
    manifest = {
        "schema_version": 1,
        "platform": args.platform,
        "build_number": build_number,
        "app_version": app_version,
        "application_id": application_id,
        "api_base_url": normalize_api_base_url(args.api_base_url),
        "source_run_id": _required_environment("GITHUB_RUN_ID"),
        "source_branch": _required_environment("GITHUB_REF_NAME"),
        "source_workflow_ref": _required_environment("GITHUB_WORKFLOW_REF"),
        "source_commit": _required_environment("GITHUB_SHA"),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def validate_source_run(run, run_id, workflow_path, branch):
    if str(run.get("id")) != str(run_id):
        raise ValueError("GitHub Actions source run ID does not match the requested run.")
    if str(run.get("path", "")).lstrip("/") != workflow_path:
        raise ValueError(f"Source run must use {workflow_path}.")
    if run.get("event") != "workflow_dispatch":
        raise ValueError("Source run must be a manually dispatched workflow.")
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        raise ValueError("Source upload workflow must have completed successfully.")
    if run.get("head_branch") != branch:
        raise ValueError(f"Source upload workflow must run from the {branch} branch.")


def _get_source_run(run_id):
    repository = _required_environment("GITHUB_REPOSITORY")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise ValueError("GH_TOKEN is required to verify the source workflow run.")
    if not run_id.isdecimal():
        raise ValueError("Source workflow run ID must be numeric.")

    request = urllib.request.Request(
        f"https://api.github.com/repos/{repository}/actions/runs/{run_id}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise ValueError(
            f"GitHub could not verify source run {run_id} (HTTP {error.code})."
        ) from error
    except urllib.error.URLError as error:
        raise ValueError(f"Could not reach GitHub to verify source run {run_id}.") from error


def verify_manifest(args):
    try:
        manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError("Release provenance artifact is not valid JSON.") from error
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError("Unsupported or malformed release provenance artifact.")
    if manifest.get("platform") != args.platform:
        raise ValueError("Release provenance platform does not match the requested platform.")
    if str(manifest.get("build_number")) != str(args.build_number):
        raise ValueError("Selected store build number does not match its provenance.")
    if manifest.get("app_version") != args.app_version:
        raise ValueError("Selected store app version does not match its provenance.")
    if manifest.get("application_id") != args.application_id:
        raise ValueError("Release provenance application ID does not match this store app.")
    if normalize_api_base_url(str(manifest.get("api_base_url", ""))) != PRODUCTION_API_URL:
        raise ValueError("Selected build is not configured for the production API.")
    if str(manifest.get("source_run_id")) != args.source_run_id:
        raise ValueError("Release provenance does not belong to the selected source run.")

    run = _get_source_run(args.source_run_id)
    validate_source_run(run, args.source_run_id, args.source_workflow_path, args.source_branch)
    if manifest.get("source_branch") != args.source_branch:
        raise ValueError("Release provenance branch does not match the verified source run.")
    expected_workflow_ref = (
        f"{args.source_workflow_path}@refs/heads/{args.source_branch}"
    )
    if not str(manifest.get("source_workflow_ref", "")).endswith(expected_workflow_ref):
        raise ValueError("Release provenance workflow reference does not match the source run.")
    if manifest.get("source_commit") != run.get("head_sha"):
        raise ValueError("Release provenance commit does not match the verified source run.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    build_number = commands.add_parser("build-number")
    build_number.add_argument("--pubspec", required=True)

    app_version = commands.add_parser("app-version")
    app_version.add_argument("--pubspec", required=True)

    write = commands.add_parser("write")
    write.add_argument("--platform", choices=("android", "ios"), required=True)
    write.add_argument("--build-number", required=True)
    write.add_argument("--app-version", required=True)
    write.add_argument("--application-id", required=True)
    write.add_argument("--api-base-url", required=True)
    write.add_argument("--output", required=True)

    verify = commands.add_parser("verify")
    verify.add_argument("--manifest", required=True)
    verify.add_argument("--platform", choices=("android", "ios"), required=True)
    verify.add_argument("--build-number", required=True)
    verify.add_argument("--app-version", required=True)
    verify.add_argument("--application-id", required=True)
    verify.add_argument("--source-run-id", required=True)
    verify.add_argument("--source-workflow-path", required=True)
    verify.add_argument("--source-branch", required=True)
    args = parser.parse_args()

    try:
        if args.command == "build-number":
            print(read_build_number(args.pubspec))
        elif args.command == "app-version":
            print(read_app_version(args.pubspec))
        elif args.command == "write":
            write_manifest(args)
        else:
            verify_manifest(args)
            print("Release provenance verified for the production API.")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
