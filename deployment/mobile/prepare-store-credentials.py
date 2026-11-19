#!/usr/bin/env python3
"""Validate store credentials and write temporary files for Fastlane."""
import json
import os
import sys
from pathlib import Path


def _write_private_file(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
        stream.write(content)


def _write_github_env(path: Path, values: dict[str, str]) -> None:
    with path.open('a', encoding='utf-8') as stream:
        for key, value in values.items():
            if '\n' in value or '\r' in value:
                raise ValueError(f'{key} cannot contain a newline')
            stream.write(f'{key}={value}\n')


def _load_play_json(value: str) -> dict:
    try:
        result = json.loads(value)
    except json.JSONDecodeError:
        raise ValueError('GOOGLE_PLAY_JSON must contain a service-account JSON document') from None
    if (not isinstance(result, dict) or result.get('type') != 'service_account'
            or not isinstance(result.get('client_email'), str) or not result['client_email']
            or not isinstance(result.get('private_key'), str) or not result['private_key']):
        raise ValueError('GOOGLE_PLAY_JSON is not a valid Google Play service-account key')
    return result


def prepare_play_credentials(value: str, runner_temp: Path, github_env: Path) -> Path:
    credentials = _load_play_json(value)
    path = runner_temp / 'google-play-service-account.json'
    _write_private_file(path, json.dumps(credentials))
    _write_github_env(github_env, {'GOOGLE_PLAY_JSON_KEY_PATH': str(path)})
    return path


def prepare_app_store_credentials(value: str, runner_temp: Path, github_env: Path) -> Path:
    try:
        credentials = json.loads(value)
    except json.JSONDecodeError:
        raise ValueError('APPSTORE_DEPLOYER_KEY must be a JSON credential bundle') from None
    if not isinstance(credentials, dict):
        raise ValueError('APPSTORE_DEPLOYER_KEY must be a JSON credential bundle')
    key_id = credentials.get('key_id')
    issuer_id = credentials.get('issuer_id')
    private_key = credentials.get('private_key')
    if not all(isinstance(value, str) and value.strip() for value in (key_id, issuer_id, private_key)):
        raise ValueError('APPSTORE_DEPLOYER_KEY must contain key_id, issuer_id, and private_key')
    if any('\n' in value or '\r' in value for value in (key_id, issuer_id)):
        raise ValueError('App Store Connect identifiers cannot contain a newline')
    private_key = private_key.replace('\\n', '\n').strip()
    if not (private_key.startswith('-----BEGIN PRIVATE KEY-----')
            and private_key.endswith('-----END PRIVATE KEY-----')):
        raise ValueError('APPSTORE_DEPLOYER_KEY private_key must be an unencrypted PEM key')
    path = runner_temp / 'app-store-connect.p8'
    _write_private_file(path, private_key + '\n')
    _write_github_env(github_env, {
        'APP_STORE_CONNECT_KEY_ID': key_id,
        'APP_STORE_CONNECT_ISSUER_ID': issuer_id,
        'APP_STORE_CONNECT_PRIVATE_KEY_PATH': str(path),
    })
    return path


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ('play', 'appstore'):
        raise SystemExit('Usage: prepare-store-credentials.py play|appstore')
    runner_temp = Path(os.environ['RUNNER_TEMP'])
    github_env = Path(os.environ['GITHUB_ENV'])
    try:
        if sys.argv[1] == 'play':
            prepare_play_credentials(os.environ.get('GOOGLE_PLAY_JSON', ''), runner_temp, github_env)
        else:
            prepare_app_store_credentials(os.environ.get('APPSTORE_DEPLOYER_KEY', ''), runner_temp, github_env)
    except ValueError as error:
        raise SystemExit(str(error)) from None


if __name__ == '__main__':
    main()
