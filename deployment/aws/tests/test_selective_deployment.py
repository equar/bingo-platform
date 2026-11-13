import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

AWS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('changes', AWS / 'detect-deployment-changes.py')
changes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(changes)


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
        for target, apps in [('admin', ['admin']), ('website', ['website']), ('all', ['admin', 'website'])]:
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
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
                           VITE_API_BASE_URL='https://api.example.invalid')
                if 'admin' in apps: env['ADMIN_PUBLIC_URL'] = 'https://admin.example.invalid'
                if 'website' in apps: env['PUBLIC_SITE_URL'] = 'https://site.example.invalid'
                subprocess.run(['bash', str(deploy / 'deploy-frontend.sh'), target], env=env,
                               check=True, capture_output=True)
                uploaded = [line for line in log.read_text().splitlines() if line.startswith('scp ')]
                self.assertEqual(len(uploaded), len(apps))
                for app in apps:
                    self.assertTrue(any(f'bingo-{app}/dist' in line for line in uploaded))
                self.assertIn("sudo -n bash -s -- '/tmp/bingo-frontend.ABCDEFGH' " + ' '.join(apps), log.read_text())


if __name__ == '__main__':
    unittest.main()
