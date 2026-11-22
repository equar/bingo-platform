import importlib.util
import base64
from datetime import datetime, timedelta, timezone
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

AWS = Path(__file__).resolve().parents[1]
ROOT = AWS.parents[1]
BACKEND_DEPLOY = AWS / 'deploy-backend.sh'
spec = importlib.util.spec_from_file_location('changes', AWS / 'detect-deployment-changes.py')
changes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(changes)
db_spec = importlib.util.spec_from_file_location('test_database', AWS / 'ensure-test-database.py')
test_database = importlib.util.module_from_spec(db_spec)
db_spec.loader.exec_module(test_database)
BASH = str(Path('C:/Program Files/Git/bin/bash.exe')) if Path('C:/Program Files/Git/bin/bash.exe').exists() else 'bash'


class ChangeSelectionTest(unittest.TestCase):
    def test_independent_applications(self):
        for folder, expected in [('bingo-backend', 'backend'), ('bingo-admin', 'admin'), ('bingo-website', 'website')]:
            with self.subTest(folder=folder):
                self.assertEqual(changes.affected([folder + '/src/app']), {expected})

    def test_shared_and_backend_deployment_changes(self):
        self.assertEqual(changes.affected(['deployment/aws/deploy-backend.sh']), {'backend'})
        self.assertEqual(changes.affected(['deployment/aws/deploy-frontend.sh']), {'admin', 'website'})
        self.assertEqual(changes.affected(['.github/workflows/deploy.yml']), {'backend', 'admin', 'website'})

    def test_docs_and_mobile_do_not_deploy(self):
        self.assertEqual(changes.affected(['docs/setup.md', 'bingo-admin/README.md', 'bingo-player/lib/main.dart']), set())

    def test_git_diff_includes_deleted_and_renamed_application_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)
            git('init')
            git('config', 'user.email', 'test@example.invalid')
            git('config', 'user.name', 'Test')
            (root / 'bingo-admin').mkdir()
            source = root / 'bingo-admin/old.ts'
            source.write_text('old')
            git('add', '.')
            git('commit', '-m', 'Initial')
            (root / 'bingo-backend').mkdir()
            source.rename(root / 'bingo-backend/new.java')
            git('add', '-A')
            git('commit', '-m', 'Move')
            output = root / 'outputs'
            subprocess.run([sys.executable, str(AWS / 'detect-deployment-changes.py')], cwd=root,
                           env=dict(os.environ, GITHUB_OUTPUT=str(output)), check=True, capture_output=True)
            self.assertIn('backend=true', output.read_text())
            self.assertIn('admin=true', output.read_text())
            self.assertIn('website=false', output.read_text())

    def test_manual_targets_do_not_need_git_history(self):
        for target, selected in [('all', {'backend', 'admin', 'website'}), ('backend', {'backend'}),
                                 ('frontend', {'admin', 'website'}), ('admin', {'admin'}), ('website', {'website'})]:
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / 'outputs'
                subprocess.run([sys.executable, str(AWS / 'detect-deployment-changes.py'), '--target', target],
                               cwd=directory, env=dict(os.environ, GITHUB_OUTPUT=str(output)),
                               check=True, capture_output=True)
                for app in ['backend', 'admin', 'website']:
                    self.assertIn(f'{app}={str(app in selected).lower()}', output.read_text())


@unittest.skipIf(os.name == 'nt', 'Requires a POSIX shell and PATH semantics')
class FrontendDeploymentTest(unittest.TestCase):
    def test_only_selected_frontends_are_required_and_uploaded(self):
        for protocol in ['https', 'http']:
            for target, apps in [('admin', ['admin']), ('website', ['website']), ('all', ['admin', 'website'])]:
                with self.subTest(protocol=protocol, target=target), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    deploy = root / 'deployment/aws'
                    deploy.mkdir(parents=True)
                    (root / 'deployment/lib').mkdir(parents=True)
                    shutil.copy(ROOT / 'deployment/lib/pinned-ssh-host.sh', root / 'deployment/lib')
                    host_private = root / 'test-host-key'
                    subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(host_private)], check=True)
                    host_public = Path(str(host_private) + '.pub').read_text().strip()
                    shutil.copy(AWS / 'deploy-frontend.sh', deploy)
                    for app in apps:
                        dist = root / f'bingo-{app}/dist'
                        dist.mkdir(parents=True)
                        (dist / 'index.html').write_text('test')
                    binaries = root / 'bin'
                    binaries.mkdir()
                    log = root / 'commands'
                    for command in ['ssh', 'scp', 'curl']:
                        script = binaries / command
                        script.write_text('''#!/bin/bash
printf '%s %s\\n' "$(basename "$0")" "$*" >> "$DEPLOY_TEST_LOG"
case "$(basename "$0")" in
 ssh) if [[ "$*" == *mktemp* ]]; then echo /home/deploy/.cache/bingo-deploy/frontend.ABCDEFGH; else cat >/dev/null; fi ;;
 curl) echo '{"status":"UP"}' ;;
esac
''')
                        script.chmod(0o755)
                    env = dict(os.environ, PATH=str(binaries).replace('\\', '/') + ':' + os.environ['PATH'].replace(';', ':'),
                               DEPLOY_TEST_LOG=str(log), SERVER='test@example.invalid', SSH_KEY='/unused',
                               DEPLOY_SSH_HOST_KEY=host_public,
                               DEPLOY_ENVIRONMENT='test' if protocol == 'http' else 'prod',
                               ALLOW_INSECURE_HTTP='true' if protocol == 'http' else 'false',
                               VITE_API_BASE_URL=f'{protocol}://api.example.invalid')
                    if 'admin' in apps: env['ADMIN_PUBLIC_URL'] = f'{protocol}://admin.example.invalid'
                    if 'website' in apps: env['PUBLIC_SITE_URL'] = f'{protocol}://site.example.invalid'
                    deployed = subprocess.run([BASH, str(deploy / 'deploy-frontend.sh'), target], env=env,
                                              capture_output=True, text=True)
                    self.assertEqual(deployed.returncode, 0, deployed.stderr)
                    uploaded = [line for line in log.read_text().splitlines() if line.startswith('scp ')]
                    self.assertEqual(len(uploaded), len(apps))
                    for app in apps:
                        self.assertTrue(any(f'bingo-{app}/dist' in line for line in uploaded))
                    self.assertIn("sudo -n bash -s -- '/home/deploy/.cache/bingo-deploy/frontend.ABCDEFGH' " + ' '.join(apps), log.read_text())

    def test_http_is_rejected_without_test_opt_in(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            deploy = root / 'deployment/aws'
            deploy.mkdir(parents=True)
            (root / 'deployment/lib').mkdir(parents=True)
            shutil.copy(ROOT / 'deployment/lib/pinned-ssh-host.sh', root / 'deployment/lib')
            host_private = root / 'test-host-key'
            subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(host_private)], check=True)
            host_public = Path(str(host_private) + '.pub').read_text().strip()
            script = shutil.copy(AWS / 'deploy-frontend.sh', deploy)
            result = subprocess.run(
                [BASH, str(script), 'admin'],
                env=dict(os.environ, SERVER='test@example.invalid', SSH_KEY='/unused',
                         DEPLOY_SSH_HOST_KEY=host_public,
                         DEPLOY_ENVIRONMENT='test',
                         VITE_API_BASE_URL='http://api.example.invalid',
                         ADMIN_PUBLIC_URL='http://admin.example.invalid'),
                capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('HTTP is allowed only for the test deployment', result.stderr)
            production_result = subprocess.run(
                [BASH, str(script), 'admin'],
                env=dict(os.environ, SERVER='test@example.invalid', SSH_KEY='/unused',
                         DEPLOY_SSH_HOST_KEY=host_public,
                         DEPLOY_ENVIRONMENT='prod', ALLOW_INSECURE_HTTP='true',
                         VITE_API_BASE_URL='http://api.example.invalid',
                         ADMIN_PUBLIC_URL='http://admin.example.invalid'),
                capture_output=True, text=True,
            )
            self.assertNotEqual(production_result.returncode, 0)
            self.assertIn('DEPLOY_ENVIRONMENT is test', production_result.stderr)


class DeploymentUrlValidationTest(unittest.TestCase):
    def test_database_tls_settings_must_be_explicit_and_environment_appropriate(self):
        base = {
            'DEPLOY_ENVIRONMENT': 'test',
            'MYSQL_SSL_MODE': 'DISABLED',
            'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'true',
        }
        for changes, expected_error in [
            ({'MYSQL_SSL_MODE': ''}, 'MYSQL_SSL_MODE must be explicitly set'),
            ({'MYSQL_SSL_MODE': 'PREFERRED'}, 'MYSQL_SSL_MODE must be explicitly set'),
            ({'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': ''}, 'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL must be explicitly set'),
            ({'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'false'}, 'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL must be explicitly set'),
        ]:
            with self.subTest(changes=changes):
                result = subprocess.run(
                    [sys.executable, str(AWS / 'validate-config.py')],
                    env={**os.environ, **base, **changes}, capture_output=True, text=True,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected_error, result.stderr)

    def test_http_origins_are_allowed_only_for_test(self):
        base_env = {
            'BINGO_FLOAT_BANK_ENCRYPTION_KEY': base64.b64encode(b'0123456789abcdef0123456789abcdef').decode(),
            'MYSQL_SSL_MODE': 'DISABLED',
            'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'true',
            'VITE_API_BASE_URL': 'http://api.test.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'http://admin.test.bakaroo.com',
            'PUBLIC_SITE_URL': 'http://test.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'false',
            'CORS_ALLOWED_ORIGINS': 'http://admin.test.bakaroo.com,http://test.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'http://admin.test.bakaroo.com,http://test.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'http://admin.test.bakaroo.com/activate-account',
            'APP_EMAIL_PROVIDER': 'smtp',
            'SPRING_MAIL_USERNAME': 'test@example.invalid',
        }
        test_result = subprocess.run(
            [sys.executable, str(AWS / 'validate-config.py')],
            env=dict(os.environ, **base_env, DEPLOY_ENVIRONMENT='test'),
            capture_output=True, text=True,
        )
        self.assertEqual(test_result.returncode, 0, test_result.stderr)

        wrong_environment = {
            **base_env,
            'VITE_API_BASE_URL': 'https://api.bingo.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'https://admin.bingo.bakaroo.com',
            'PUBLIC_SITE_URL': 'https://bingo.bakaroo.com',
            'CORS_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'https://admin.bingo.bakaroo.com/activate-account',
        }
        wrong_environment_result = subprocess.run(
            [sys.executable, str(AWS / 'validate-config.py')],
            env=dict(os.environ, **wrong_environment, DEPLOY_ENVIRONMENT='test'),
            capture_output=True, text=True,
        )
        self.assertNotEqual(wrong_environment_result.returncode, 0)
        self.assertIn('do not match DEPLOY_ENVIRONMENT', wrong_environment_result.stderr)

        production_result = subprocess.run(
            [sys.executable, str(AWS / 'validate-config.py')],
            env=dict(os.environ, **base_env, DEPLOY_ENVIRONMENT='prod'),
            capture_output=True, text=True,
        )
        self.assertNotEqual(production_result.returncode, 0)

    def test_email_provider_must_be_explicit_and_graph_aliases_are_rejected(self):
        base_env = {
            'BINGO_FLOAT_BANK_ENCRYPTION_KEY': base64.b64encode(b'0123456789abcdef0123456789abcdef').decode(),
            'DEPLOY_ENVIRONMENT': 'test',
            'MYSQL_SSL_MODE': 'DISABLED',
            'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'true',
            'VITE_API_BASE_URL': 'http://api.test.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'http://admin.test.bakaroo.com',
            'PUBLIC_SITE_URL': 'http://test.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'false',
            'CORS_ALLOWED_ORIGINS': 'http://admin.test.bakaroo.com,http://test.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'http://admin.test.bakaroo.com,http://test.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'http://admin.test.bakaroo.com/activate-account',
        }
        missing_provider = subprocess.run(
            [sys.executable, str(AWS / 'validate-config.py')],
            env=dict(os.environ, **base_env),
            capture_output=True, text=True,
        )
        self.assertNotEqual(missing_provider.returncode, 0)
        self.assertIn('APP_EMAIL_PROVIDER must be explicitly set', missing_provider.stderr)

        legacy_graph = {
            **base_env,
            'APP_EMAIL_PROVIDER': 'graph',
            'APP_EMAIL_FROM': 'sender@example.invalid',
            'GRAPH_TENANT_ID': 'tenant',
            'GRAPH_CLIENT_ID': 'client',
            'GRAPH_CLIENT_SECRET': 'secret',
        }
        graph_result = subprocess.run(
            [sys.executable, str(AWS / 'validate-config.py')],
            env=dict(os.environ, **legacy_graph),
            capture_output=True, text=True,
        )
        self.assertNotEqual(graph_result.returncode, 0)
        self.assertIn('MICROSOFT_GRAPH_TENANT_ID is required', graph_result.stderr)

    def test_production_admin_bootstrap_requires_explicit_unexpired_short_approval(self):
        base_env = {
            'DEPLOY_ENVIRONMENT': 'prod',
            'BINGO_FLOAT_BANK_ENCRYPTION_KEY': base64.b64encode(b'0123456789abcdef0123456789abcdef').decode(),
            'MYSQL_SSL_MODE': 'VERIFY_IDENTITY',
            'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'false',
            'VITE_API_BASE_URL': 'https://api.bingo.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'https://admin.bingo.bakaroo.com',
            'PUBLIC_SITE_URL': 'https://bingo.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'true',
            'CORS_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'https://admin.bingo.bakaroo.com/activate-account',
            'APP_EMAIL_PROVIDER': 'smtp',
            'SPRING_MAIL_USERNAME': 'admin@example.invalid',
            'ADMIN_EMAIL_BOOTSTRAP_ENABLED': 'true',
            'ADMIN_BOOTSTRAP_EMAILS': 'first@example.invalid',
            'ADMIN_BOOTSTRAP_ENABLED': 'false',
        }
        missing = subprocess.run([sys.executable, str(AWS / 'validate-config.py')], env=dict(os.environ, **base_env),
                                 capture_output=True, text=True)
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn('approve_initial_admin_bootstrap=true', missing.stderr)

        approved = {
            **base_env,
            'ADMIN_BOOTSTRAP_APPROVED': 'true',
            'ADMIN_BOOTSTRAP_APPROVAL_EXPIRES_AT': (datetime.now(timezone.utc) + timedelta(minutes=30))
            .replace(microsecond=0).isoformat().replace('+00:00', 'Z'),
        }
        success = subprocess.run([sys.executable, str(AWS / 'validate-config.py')], env=dict(os.environ, **approved),
                                 capture_output=True, text=True)
        self.assertEqual(success.returncode, 0, success.stderr)

        expired = {**approved, 'ADMIN_BOOTSTRAP_APPROVAL_EXPIRES_AT': '2020-01-01T00:00:00Z'}
        expired_result = subprocess.run([sys.executable, str(AWS / 'validate-config.py')], env=dict(os.environ, **expired),
                                        capture_output=True, text=True)
        self.assertNotEqual(expired_result.returncode, 0)
        self.assertIn('unexpired and expire within 60 minutes', expired_result.stderr)

    def test_production_configuration_rejects_missing_or_invalid_float_encryption_key(self):
        valid = {
            'DEPLOY_ENVIRONMENT': 'prod',
            'MYSQL_SSL_MODE': 'VERIFY_IDENTITY',
            'MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL': 'false',
            'BINGO_FLOAT_BANK_ENCRYPTION_KEY': base64.b64encode(b'0123456789abcdef0123456789abcdef').decode(),
            'VITE_API_BASE_URL': 'https://api.bingo.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'https://admin.bingo.bakaroo.com',
            'PUBLIC_SITE_URL': 'https://bingo.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'true',
            'CORS_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'https://admin.bingo.bakaroo.com,https://bingo.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'https://admin.bingo.bakaroo.com/activate-account',
            'APP_EMAIL_PROVIDER': 'smtp',
            'SPRING_MAIL_USERNAME': 'admin@example.invalid',
        }
        for override in [
            {'BINGO_FLOAT_BANK_ENCRYPTION_KEY': ''},
            {'BINGO_FLOAT_BANK_ENCRYPTION_KEY': 'not-base64'},
            {'BINGO_FLOAT_BANK_ENCRYPTION_KEY': base64.b64encode(b'too-short').decode()},
        ]:
            with self.subTest(override=override):
                result = subprocess.run([sys.executable, str(AWS / 'validate-config.py')],
                env={**os.environ, **valid, **override}, capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('BINGO_FLOAT_BANK_ENCRYPTION_KEY must be Base64 encoding of exactly 32 bytes', result.stderr)
        production_yaml = (ROOT / 'bingo-backend/src/main/resources/application-prod.yml').read_text()
        self.assertIn('bank-account-encryption-key: ${BINGO_FLOAT_BANK_ENCRYPTION_KEY}', production_yaml)
        self.assertNotIn('bank-account-encryption-key: ${BINGO_FLOAT_BANK_ENCRYPTION_KEY:}', production_yaml)

    def test_production_workflow_has_no_tofu_or_default_admin_recipients(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        self.assertNotIn('ssh-keyscan', workflow)
        self.assertIn('vars.DEPLOY_SSH_HOST_KEY', workflow)
        self.assertIn('approve_initial_admin_bootstrap', workflow)
        self.assertIn('ADMIN_BOOTSTRAP_APPROVAL_RUN_ID', workflow)
        self.assertNotIn('apps@bakaroo.com,equarhishe@gmail.com', workflow)
        self.assertIn("'false' }}", workflow)
        for relative in ('deployment/aws/deploy-backend.sh', 'deployment/aws/deploy-frontend.sh',
                         'deployment/admin/deploy-admin.sh', 'deployment/admin/rollback-admin.sh'):
            script = (ROOT / relative).read_text()
            self.assertIn('configure_pinned_ssh_host', script)
            for line in script.splitlines():
                if line.lstrip().startswith(('ssh ', 'scp ')):
                    self.assertIn('${SSH_OPTIONS[@]}', line, relative + ': ' + line)
        self.assertIn("'ADMIN_BOOTSTRAP_APPROVAL_RUN_ID':'", (ROOT / 'deployment/aws/deploy-backend.sh').read_text())


class PinnedSshHostKeyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.private_key = Path(cls.temp.name) / 'host'
        subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(cls.private_key)], check=True)
        cls.public_key = Path(str(cls.private_key) + '.pub').read_text().strip()

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_missing_or_invalid_pin_fails_before_ssh(self):
        helper = ROOT / 'deployment/lib/pinned-ssh-host.sh'
        known_hosts = Path(self.temp.name) / 'known_hosts-missing'
        command = [BASH, '-c', 'source "$1"; configure_pinned_ssh_host "$2" "$3"',
                   'pinned-test', str(helper), 'deploy@example.invalid', str(known_hosts)]
        missing = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(missing.returncode, 0)
        invalid = subprocess.run(command, env=dict(os.environ, DEPLOY_SSH_HOST_KEY='ssh-ed25519 INVALID'),
                                 capture_output=True, text=True)
        self.assertNotEqual(invalid.returncode, 0)
        self.assertFalse(known_hosts.exists())

    def test_known_hosts_contains_the_supplied_pin_without_network_lookup(self):
        helper = ROOT / 'deployment/lib/pinned-ssh-host.sh'
        known_hosts = Path(self.temp.name) / 'known_hosts-pinned'
        result = subprocess.run(
            [BASH, '-c', 'source "$1"; configure_pinned_ssh_host "$2" "$3"; printf "%s\\n" "${SSH_OPTIONS[@]}"', 'pinned-test',
             str(helper), 'deploy@example.invalid', str(known_hosts)],
            env=dict(os.environ, DEPLOY_SSH_HOST_KEY=self.public_key), capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('StrictHostKeyChecking=yes', result.stdout)
        self.assertIn('GlobalKnownHostsFile=/dev/null', result.stdout)
        expected_pin = ' '.join(self.public_key.split()[:2])
        self.assertIn('example.invalid ' + expected_pin, known_hosts.read_text())

    @unittest.skipIf(os.name == 'nt', 'Shell expects a POSIX python3 command')
    def test_mobile_release_url_allows_http_only_for_test_api_host(self):
        validator = ROOT / 'bingo-player/tooling/validate_release_api_base_url.sh'
        accepted = subprocess.run(
            [BASH, str(validator), 'http://api.test.bakaroo.com', '--allow-test-http'],
            capture_output=True, text=True,
        )
        self.assertEqual(accepted.returncode, 0, accepted.stderr)

        blocked_without_opt_in = subprocess.run(
            [BASH, str(validator), 'http://api.test.bakaroo.com'],
            capture_output=True, text=True,
        )
        self.assertNotEqual(blocked_without_opt_in.returncode, 0)

        rejected = subprocess.run(
            [BASH, str(validator), 'http://api.example.invalid', '--allow-test-http'],
            capture_output=True, text=True,
        )
        self.assertNotEqual(rejected.returncode, 0)

    def test_test_ec2_bootstrap_reloads_nginx_after_installing_vhosts(self):
        bootstrap = (
            ROOT / 'deployment/terraform/templates/test-ec2-bootstrap.sh.tftpl'
        ).read_text()
        self.assertLess(bootstrap.index('nginx -t'), bootstrap.index('systemctl reload nginx'))
        self.assertIn('systemctl enable --now nginx\nsystemctl reload nginx', bootstrap)

class BackendFlywayPreflightTest(unittest.TestCase):
    def test_test_and_production_deployments_use_isolated_schema_and_ports(self):
        source = BACKEND_DEPLOY.read_text()
        self.assertIn(
            'test) expected_database=bingotest; expected_port=8091; expected_ssl_mode=DISABLED; allow_public_key_retrieval=true ;;',
            source,
        )
        self.assertIn(
            'prod) expected_database=bingo; expected_port=8081; expected_ssl_mode=VERIFY_IDENTITY; allow_public_key_retrieval=false ;;',
            source,
        )
        production_config = (ROOT / 'bingo-backend/src/main/resources/application-prod.yml').read_text()
        self.assertIn('MYSQL_SSL_MODE:VERIFY_IDENTITY', production_config)
        self.assertIn('MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL:false', production_config)
        self.assertIn('http://127.0.0.1:$server_port/actuator/health', source)
        self.assertNotIn('http://127.0.0.1:8081/actuator/health', source)

        service = (AWS / 'bingo-backend.service').read_text()
        self.assertNotIn('Environment=SERVER_PORT=8081', service)
        self.assertNotIn('-Xmx1g', service)
        self.assertIn('EnvironmentFile=/opt/bingo/.env', service)

        nginx = (AWS / 'nginx-bingo.conf.template').read_text()
        self.assertEqual(nginx.count('http://127.0.0.1:${API_PORT}'), 3)
        deployment = BACKEND_DEPLOY.read_text()
        self.assertIn("MYSQL_HOST:-}", deployment)
        self.assertIn("MYSQL_SSL_MODE", deployment)

    def test_empty_failed_migration_list_is_null_safe(self):
        source = BACKEND_DEPLOY.read_text()
        self.assertIn(
            "SELECT COALESCE(GROUP_CONCAT(version ORDER BY installed_rank SEPARATOR ', '), '')",
            source,
        )

    def test_v41_repair_stops_then_rechecks_before_deleting(self):
        source = BACKEND_DEPLOY.read_text()
        repair = source.split('repair_failed_removed_v41_migration() {', 1)[1].split(
            '\n}\n\nschema_version_before_deploy=', 1
        )[0]
        validation = repair.index("if failed_rows != [['41', removed_migration]]:")
        stop = repair.index("subprocess.run(['systemctl', 'stop', 'bingo-backend'], check=True)")
        recheck = repair.index("failed = subprocess.run(base + ['--execute', failed_query]", stop)
        deletion = repair.index("DELETE FROM flyway_schema_history", recheck)
        self.assertLess(validation, stop)
        self.assertLess(stop, recheck)
        self.assertLess(recheck, deletion)
        self.assertEqual(repair.count("failed = subprocess.run(base + ['--execute', failed_query]"), 2)


class TestDatabaseBootstrapTest(unittest.TestCase):
    def test_creates_only_local_test_schema_and_scoped_user(self):
        sql = test_database.build_sql({
            'MYSQL_HOST': '127.0.0.1',
            'MYSQL_DATABASE': 'bingotest',
            'MYSQL_USER': 'bingo_test',
            'MYSQL_PASSWORD': "secret'\\value",
        })
        self.assertIn('CREATE DATABASE IF NOT EXISTS `bingotest`', sql)
        self.assertIn("CREATE USER IF NOT EXISTS 'bingo_test'@'127.0.0.1'", sql)
        self.assertIn("IDENTIFIED BY 'secret\\'\\\\value'", sql)
        self.assertIn('GRANT ALL PRIVILEGES ON `bingotest`.*', sql)
        self.assertNotIn('`bingo`', sql)

    def test_rejects_nonlocal_database_and_unsafe_usernames(self):
        settings = {
            'MYSQL_HOST': 'database.example.invalid',
            'MYSQL_DATABASE': 'bingotest',
            'MYSQL_USER': 'bingo_test',
            'MYSQL_PASSWORD': 'secret',
        }
        with self.assertRaises(ValueError):
            test_database.build_sql(settings)
        settings['MYSQL_HOST'] = '127.0.0.1'
        settings['MYSQL_USER'] = "bingo'@'localhost"
        with self.assertRaises(ValueError):
            test_database.build_sql(settings)


if __name__ == '__main__':
    unittest.main()
