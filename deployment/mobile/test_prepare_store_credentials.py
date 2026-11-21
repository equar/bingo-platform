import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name('prepare-store-credentials.py')
SPEC = importlib.util.spec_from_file_location('prepare_store_credentials', MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PrepareStoreCredentialsTest(unittest.TestCase):
    def test_play_credentials_write_protected_file_and_path(self):
        credentials = {
            'type': 'service_account',
            'client_email': 'release@example.invalid',
            'private_key': 'synthetic-key',
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            github_env = root / 'github-env'
            credential_path = MODULE.prepare_play_credentials(
                json.dumps(credentials), root, github_env)

            self.assertEqual(json.loads(credential_path.read_text()), credentials)
            self.assertEqual(credential_path.stat().st_mode & 0o777, 0o600)
            self.assertIn(f'GOOGLE_PLAY_JSON_KEY_PATH={credential_path}', github_env.read_text())

    def test_app_store_bundle_extracts_identifiers_and_protected_key(self):
        credentials = {
            'key_id': 'KEY123',
            'issuer_id': 'issuer-uuid',
            'private_key': '-----BEGIN PRIVATE KEY-----\nsynthetic\n-----END PRIVATE KEY-----',
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            github_env = root / 'github-env'
            path = MODULE.prepare_app_store_credentials(
                json.dumps(credentials), root, github_env)

            self.assertEqual(path.read_text(), credentials['private_key'] + '\n')
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            values = github_env.read_text()
            self.assertIn('APP_STORE_CONNECT_KEY_ID=KEY123', values)
            self.assertIn('APP_STORE_CONNECT_ISSUER_ID=issuer-uuid', values)
            self.assertIn(f'APP_STORE_CONNECT_PRIVATE_KEY_PATH={path}', values)

    def test_app_store_bundle_rejects_placeholder_secret(self):
        with self.assertRaisesRegex(ValueError, 'JSON credential bundle'):
            MODULE.prepare_app_store_credentials('temp', Path('/tmp'), Path('/tmp/env'))


if __name__ == '__main__':
    unittest.main()
