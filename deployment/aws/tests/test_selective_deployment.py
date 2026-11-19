import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
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
            subprocess.run(['python3', str(AWS / 'detect-deployment-changes.py')], cwd=root,
                           env=dict(os.environ, GITHUB_OUTPUT=str(output)), check=True, capture_output=True)
            self.assertIn('backend=true', output.read_text())
            self.assertIn('admin=true', output.read_text())
            self.assertIn('website=false', output.read_text())

    def test_manual_targets_do_not_need_git_history(self):
        for target, selected in [('all', {'backend', 'admin', 'website'}), ('backend', {'backend'}),
                                 ('frontend', {'admin', 'website'}), ('admin', {'admin'}), ('website', {'website'})]:
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / 'outputs'
                subprocess.run(['python3', str(AWS / 'detect-deployment-changes.py'), '--target', target],
                               cwd=directory, env=dict(os.environ, GITHUB_OUTPUT=str(output)),
                               check=True, capture_output=True)
                for app in ['backend', 'admin', 'website']:
                    self.assertIn(f'{app}={str(app in selected).lower()}', output.read_text())


class FrontendDeploymentTest(unittest.TestCase):
    def test_only_selected_frontends_are_required_and_uploaded(self):
        for protocol in ['https', 'http']:
            for target, apps in [('admin', ['admin']), ('website', ['website']), ('all', ['admin', 'website'])]:
                with self.subTest(protocol=protocol, target=target), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    deploy = root / 'deployment/aws'
                    deploy.mkdir(parents=True)
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
 ssh) if [[ "$*" == *mktemp* ]]; then echo /tmp/bingo-frontend.ABCDEFGH; else cat >/dev/null; fi ;;
 curl) echo '{"status":"UP"}' ;;
esac
''')
                        script.chmod(0o755)
                    env = dict(os.environ, PATH=str(binaries)+os.pathsep+os.environ['PATH'],
                               DEPLOY_TEST_LOG=str(log), SERVER='test@example.invalid', SSH_KEY='/unused',
                               DEPLOY_ENVIRONMENT='test' if protocol == 'http' else 'prod',
                               ALLOW_INSECURE_HTTP='true' if protocol == 'http' else 'false',
                               VITE_API_BASE_URL=f'{protocol}://api.example.invalid')
                    if 'admin' in apps: env['ADMIN_PUBLIC_URL'] = f'{protocol}://admin.example.invalid'
                    if 'website' in apps: env['PUBLIC_SITE_URL'] = f'{protocol}://site.example.invalid'
                    subprocess.run(['bash', str(deploy / 'deploy-frontend.sh'), target], env=env,
                                   check=True, capture_output=True)
                    uploaded = [line for line in log.read_text().splitlines() if line.startswith('scp ')]
                    self.assertEqual(len(uploaded), len(apps))
                    for app in apps:
                        self.assertTrue(any(f'bingo-{app}/dist' in line for line in uploaded))
                    self.assertIn("sudo -n bash -s -- '/tmp/bingo-frontend.ABCDEFGH' " + ' '.join(apps), log.read_text())

    def test_http_is_rejected_without_test_opt_in(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            deploy = root / 'deployment/aws'
            deploy.mkdir(parents=True)
            script = shutil.copy(AWS / 'deploy-frontend.sh', deploy)
            result = subprocess.run(
                ['bash', str(script), 'admin'],
                env=dict(os.environ, SERVER='test@example.invalid', SSH_KEY='/unused',
                         DEPLOY_ENVIRONMENT='test',
                         VITE_API_BASE_URL='http://api.example.invalid',
                         ADMIN_PUBLIC_URL='http://admin.example.invalid'),
                capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('HTTP is allowed only for the test deployment', result.stderr)
            production_result = subprocess.run(
                ['bash', str(script), 'admin'],
                env=dict(os.environ, SERVER='test@example.invalid', SSH_KEY='/unused',
                         DEPLOY_ENVIRONMENT='prod', ALLOW_INSECURE_HTTP='true',
                         VITE_API_BASE_URL='http://api.example.invalid',
                         ADMIN_PUBLIC_URL='http://admin.example.invalid'),
                capture_output=True, text=True,
            )
            self.assertNotEqual(production_result.returncode, 0)
            self.assertIn('DEPLOY_ENVIRONMENT is test', production_result.stderr)


class DeploymentUrlValidationTest(unittest.TestCase):
    def test_http_origins_are_allowed_only_for_test(self):
        base_env = {
            'VITE_API_BASE_URL': 'http://api.test.bingo.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'http://admin.test.bingo.bakaroo.com',
            'PUBLIC_SITE_URL': 'http://test.bingo.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'false',
            'CORS_ALLOWED_ORIGINS': 'http://admin.test.bingo.bakaroo.com,http://test.bingo.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'http://admin.test.bingo.bakaroo.com,http://test.bingo.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'http://admin.test.bingo.bakaroo.com/activate-account',
            'APP_EMAIL_PROVIDER': 'smtp',
            'SPRING_MAIL_USERNAME': 'test@example.invalid',
        }
        test_result = subprocess.run(
            ['python3', str(AWS / 'validate-config.py')],
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
            ['python3', str(AWS / 'validate-config.py')],
            env=dict(os.environ, **wrong_environment, DEPLOY_ENVIRONMENT='test'),
            capture_output=True, text=True,
        )
        self.assertNotEqual(wrong_environment_result.returncode, 0)
        self.assertIn('do not match DEPLOY_ENVIRONMENT', wrong_environment_result.stderr)

        production_result = subprocess.run(
            ['python3', str(AWS / 'validate-config.py')],
            env=dict(os.environ, **base_env, DEPLOY_ENVIRONMENT='prod'),
            capture_output=True, text=True,
        )
        self.assertNotEqual(production_result.returncode, 0)

    def test_email_provider_must_be_explicit_and_graph_aliases_are_rejected(self):
        base_env = {
            'DEPLOY_ENVIRONMENT': 'test',
            'VITE_API_BASE_URL': 'http://api.test.bingo.bakaroo.com',
            'ADMIN_PUBLIC_URL': 'http://admin.test.bingo.bakaroo.com',
            'PUBLIC_SITE_URL': 'http://test.bingo.bakaroo.com',
            'PUBLIC_SITE_INDEXABLE': 'false',
            'CORS_ALLOWED_ORIGINS': 'http://admin.test.bingo.bakaroo.com,http://test.bingo.bakaroo.com',
            'WEBSOCKET_ALLOWED_ORIGINS': 'http://admin.test.bingo.bakaroo.com,http://test.bingo.bakaroo.com',
            'ADMIN_ACCOUNT_ACTIVATION_URL': 'http://admin.test.bingo.bakaroo.com/activate-account',
        }
        missing_provider = subprocess.run(
            ['python3', str(AWS / 'validate-config.py')],
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
            ['python3', str(AWS / 'validate-config.py')],
            env=dict(os.environ, **legacy_graph),
            capture_output=True, text=True,
        )
        self.assertNotEqual(graph_result.returncode, 0)
        self.assertIn('MICROSOFT_GRAPH_TENANT_ID is required', graph_result.stderr)

    def test_mobile_release_url_allows_http_only_for_test_api_host(self):
        validator = ROOT / 'bingo-player/tooling/validate_release_api_base_url.sh'
        accepted = subprocess.run(
            ['bash', str(validator), 'http://api.test.bingo.bakaroo.com'],
            capture_output=True, text=True,
        )
        self.assertEqual(accepted.returncode, 0, accepted.stderr)

        rejected = subprocess.run(
            ['bash', str(validator), 'http://api.example.invalid'],
            capture_output=True, text=True,
        )
        self.assertNotEqual(rejected.returncode, 0)
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
