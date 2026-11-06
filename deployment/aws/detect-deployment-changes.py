"""Select independently deployable applications from a merged commit's diff."""
import argparse
import os
import subprocess
from pathlib import Path


def affected(paths):
    selected = set()
    for path in paths:
        if path.endswith('.md') or path.startswith('docs/'):
            continue
        for folder, target in [('bingo-backend/', 'backend'), ('bingo-admin/', 'admin'), ('bingo-website/', 'website')]:
            if path.startswith(folder):
                selected.add(target)
        if path == '.github/workflows/deploy.yml':
            selected.update(('backend', 'admin', 'website'))
        elif path.startswith('deployment/aws/'):
            if Path(path).name in ('deploy-backend.sh', 'bingo-backend.service', 'setup-rds-truststore.sh'):
                selected.add('backend')
            elif Path(path).name == 'deploy-frontend.sh':
                selected.update(('admin', 'website'))
            else:
                selected.update(('backend', 'admin', 'website'))
    return selected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', default='auto', choices=['auto', 'all', 'backend', 'frontend', 'admin', 'website'])
    args = parser.parse_args()
    if args.target == 'auto':
        # First-parent diff captures exactly what this merge introduced to main, including deletions.
        paths = subprocess.check_output(['git', 'diff', '--name-only', '--no-renames', '-z', 'HEAD^1', 'HEAD']).decode().split('\0')
        selected = affected(paths)
    elif args.target == 'all':
        selected = {'backend', 'admin', 'website'}
    elif args.target == 'frontend':
        selected = {'admin', 'website'}
    else:
        selected = {args.target}
    values = {target: str(target in selected).lower() for target in ('backend', 'admin', 'website')}
    values['any'] = str(bool(selected)).lower()
    values['frontend_target'] = 'all' if {'admin', 'website'} <= selected else ('admin' if 'admin' in selected else 'website')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        for key, value in values.items():
            output.write(f'{key}={value}\n')
    print('Deploy: ' + (', '.join(sorted(selected)) or 'nothing (no application changes)'))


if __name__ == '__main__':
    main()
