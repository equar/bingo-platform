#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [component, ...args] = process.argv.slice(2);
const ci = args.includes('--ci');
const requestedApp = valueAfter('--app');
const flutterVersion = valueAfter('--flutter-version');
const mavenEnv = process.env;

function valueAfter(flag) {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : undefined;
}

function run(command, commandArgs, cwd, env = process.env) {
  const printable = [command, ...commandArgs].join(' ');
  console.log(`\n> ${printable}  (cwd: ${path.relative(repoRoot, cwd) || '.'})`);
  const result = spawnSync(command, commandArgs, {
    cwd,
    env,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
  if (result.error) {
    throw new Error(`${printable}\n${result.error.message}`);
  }
  if (result.status !== 0) {
    throw new Error(`${printable}\nCommand exited with code ${result.status ?? 'unknown'}.`);
  }
}

function requireTool(command, guidance) {
  const check = spawnSync(command, ['--version'], { encoding: 'utf8', shell: process.platform === 'win32' });
  if (check.error || check.status !== 0) {
    throw new Error(`Required tool '${command}' is unavailable. ${guidance}`);
  }
}

function verifyNode() {
  if (process.versions.node !== '22.13.0') {
    throw new Error(`PR validation uses Node 22.13.0. Select that exact version for local Admin and website checks (found ${process.versions.node}).`);
  }
}

function verifyFlutter(expected) {
  requireTool('flutter', 'Install Flutter stable from the version used by the matching mobile CI matrix job.');
  const result = spawnSync('flutter', ['--version', '--machine'], { encoding: 'utf8', shell: process.platform === 'win32' });
  if (result.error || result.status !== 0) throw new Error(`flutter --version --machine failed: ${result.stderr || result.error?.message}`);
  let actual;
  try { actual = JSON.parse(result.stdout).frameworkVersion; }
  catch { throw new Error(`Could not parse Flutter version output: ${result.stdout}`); }
  if (expected && actual !== expected) throw new Error(`This CI matrix check requires Flutter ${expected}; local Flutter is ${actual}.`);
  console.log(`Flutter ${actual} (CI matrix versions: 3.41.0 and 3.47.7)`);
  return actual;
}

function runAdmin() {
  const cwd = path.join(repoRoot, 'bingo-admin');
  verifyNode();
  fs.mkdirSync(path.join(cwd, 'test-results'), { recursive: true });
  run('npm', ['run', 'lint'], cwd);
  run('npm', ['run', 'test:coverage', '--', '--maxWorkers=1', '--reporter=junit', '--outputFile=test-results/vitest-junit.xml'], cwd);
  run('npx', ['tsc', '-b', '--pretty', 'false'], cwd);
  run('npm', ['run', 'build'], cwd);
}

function runWebsite() {
  const cwd = path.join(repoRoot, 'bingo-website');
  verifyNode();
  run('npm', ['run', 'check'], cwd);
  run('npm', ['run', 'validate:links'], cwd);
  run('npm', ['run', 'build'], cwd, { ...process.env, PUBLIC_SITE_URL: 'https://example.invalid' });
}

function runMobile(app) {
  if (!['bingo-player', 'bingo-agent'].includes(app)) throw new Error(`Unknown mobile app '${app}'.`);
  const cwd = path.join(repoRoot, app);
  const actualVersion = verifyFlutter(ci ? flutterVersion : undefined);
  run('flutter', ['pub', 'get'], cwd);
  run('flutter', ['gen-l10n'], cwd);
  run('flutter', ['analyze'], cwd);
  run('flutter', ['test'], cwd);
  if (app === 'bingo-player' && actualVersion === '3.47.7') {
    const validationJavaOptions = [
      process.env.JAVA_TOOL_OPTIONS,
      '-Dorg.gradle.project.allowDebugSigningForValidation=true',
    ].filter(Boolean).join(' ');
    run('flutter', ['build', 'appbundle', '--release', '--dart-define=API_BASE_URL=https://api.example.invalid'], cwd, {
      ...process.env,
      ORG_GRADLE_PROJECT_allowDebugSigningForValidation: 'true',
      // Flutter on Windows can drop ORG_GRADLE_PROJECT_* before launching Gradle.
      // Keep the same CI-only validation property available as a JVM project property.
      JAVA_TOOL_OPTIONS: validationJavaOptions,
      MOBILE_API_BASE_URL: 'https://api.example.invalid',
    });
  }
}

function runBackend() {
  const cwd = path.join(repoRoot, 'bingo-backend');
  requireTool('mvn', 'Install Maven and Java 25 as used by PR Validation Pipeline.');
  if (ci) {
    run('mvn', ['-q', 'verify'], cwd, { ...process.env, SPRING_PROFILES_ACTIVE: 'test' });
    return;
  }
  const url = mavenEnv.BINGO_MAVEN_MYSQL_URL || '';
  const username = mavenEnv.BINGO_MAVEN_MYSQL_USERNAME || '';
  const password = mavenEnv.BINGO_MAVEN_MYSQL_PASSWORD || '';
  const safeTarget = /^jdbc:mysql:\/\/(127\.0\.0\.1|localhost):3306\/bingo_qa_maven_test(?:\?[^#]*)?$/i.test(url)
    && !/createDatabaseIfNotExist\s*=\s*true/i.test(url)
    && username === 'bingo_qa_maven_runner' && password.length > 0;
  if (safeTarget) {
    console.log('Using the dedicated bingo_qa_maven_test target. MySqlIntegrationTest verifies the database identity and schema-only grants before database tests run.');
    run('mvn', ['-q', 'verify'], cwd, {
      ...process.env,
      SPRING_PROFILES_ACTIVE: 'test',
      BINGO_MAVEN_CI_DISPOSABLE: 'false',
      BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED: 'true',
    });
  } else {
    console.log('SKIPPED database-backed Maven tests: bingo-backend/.env.maven-test with the approved loopback bingo_qa_maven_test / bingo_qa_maven_runner configuration is unavailable. CI still runs `mvn -q verify` against its disposable MySQL service.');
    run('mvn', ['-q', '-DskipTests', 'package'], cwd);
  }
}

function runInfrastructure() {
  requireTool('python', 'Install Python 3.12 and run `python -m pip install cfn-lint`.');
  requireTool('cfn-lint', 'Run `python -m pip install cfn-lint`.');
  run('cfn-lint', ['deployment/aws/lightsail-stack.yaml'], repoRoot);
  run('python', ['-m', 'py_compile', 'deployment/aws/bootstrap-github-actions-config.py', 'deployment/aws/validate-config.py'], repoRoot);
  const bash = process.env.BINGO_BASH || 'bash';
  run(bash, ['-n', 'deployment/lightsail/deploy-release.sh'], repoRoot);
  for (const item of fs.readdirSync(path.join(repoRoot, 'deployment/aws')).filter((name) => name.endsWith('.sh'))) {
    run(bash, ['-n', `deployment/aws/${item}`], repoRoot);
  }
  if (process.platform === 'win32') {
    console.log('CI-only check not run locally: rendered Nginx validation requires the Linux nginx/gettext/openssl packages and sudo used by GitHub Actions.');
  }
}

async function runPreCommit() {
  const listed = spawnSync('git', ['diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z'], { cwd: repoRoot, encoding: 'buffer' });
  if (listed.status !== 0) throw new Error(`git diff --cached --name-only --diff-filter=ACMR -z failed: ${listed.stderr.toString()}`);
  const files = listed.stdout.toString('utf8').split('\0').filter(Boolean);
  const adminFiles = files.filter((file) => /^bingo-admin\/.*\.(?:js|jsx|ts|tsx|mjs|cjs)$/i.test(file));
  const dartFiles = files.filter((file) => /^bingo-(?:player|agent)\/.*\.dart$/i.test(file));
  const blob = (file) => {
    const result = spawnSync('git', ['show', `:${file}`], { cwd: repoRoot, encoding: 'buffer' });
    if (result.status !== 0) throw new Error(`git show :${file} failed: ${result.stderr.toString()}`);
    return result.stdout.toString('utf8');
  };

  if (adminFiles.length) {
    const adminRoot = path.join(repoRoot, 'bingo-admin');
    const apiPath = path.join(adminRoot, 'node_modules/eslint/lib/api.js');
    if (!fs.existsSync(apiPath)) throw new Error(`Staged Admin source needs ESLint, but ${apiPath} is missing. Run: cd bingo-admin; npm ci`);
    const { ESLint } = await import(pathToFileURL(apiPath).href);
    const eslint = new ESLint({ cwd: adminRoot });
    let failed = false;
    for (const file of adminFiles) {
      const command = `npx eslint --stdin --stdin-filename ${file}`;
      console.log(`\n> ${command}  (staged blob)`);
      const results = await eslint.lintText(blob(file), { filePath: path.join(repoRoot, file) });
      if (results.some((result) => result.errorCount > 0)) {
        failed = true;
        console.error(`FAILED FILE: ${file}\nCOMMAND: ${command}`);
        console.error(await (await eslint.loadFormatter('stylish')).format(results));
      }
    }
    if (failed) throw new Error('Fix the staged Admin lint errors, then stage the corrected content. No files were modified.');
  }

  if (dartFiles.length) {
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bingo-staged-dart-'));
    try {
      for (const file of dartFiles) {
        const stagedPath = path.join(tempRoot, file.replaceAll('/', path.sep));
        fs.mkdirSync(path.dirname(stagedPath), { recursive: true });
        fs.writeFileSync(stagedPath, blob(file));
        const command = `dart format --output=none --set-exit-if-changed ${stagedPath}`;
        console.log(`\n> ${command}  (staged blob)`);
        const result = spawnSync('dart', ['format', '--output=none', '--set-exit-if-changed', stagedPath], { encoding: 'utf8', shell: process.platform === 'win32' });
        if (result.stdout) process.stdout.write(result.stdout);
        if (result.stderr) process.stderr.write(result.stderr);
        if (result.error || result.status !== 0) {
          throw new Error(`FAILED FILE: ${file}\nCOMMAND: ${command}\n${result.stderr || result.error?.message || `Command exited with code ${result.status}.`}`);
        }
      }
    } finally {
      fs.rmSync(tempRoot, { recursive: true, force: true });
    }
  }

  if (!adminFiles.length && !dartFiles.length) console.log('No staged Admin or Dart source files need a configured fast lint/format check.');
  console.log('Staged-file checks passed.');
}

function runBackendPackage() {
  runBackend();
}

try {
  if (component === 'precommit') {
    await runPreCommit();
    process.exit(0);
  }
  switch (component) {
    case 'admin': runAdmin(); break;
    case 'website': runWebsite(); break;
    case 'mobile': runMobile(requestedApp); break;
    case 'backend': runBackendPackage(); break;
    case 'infrastructure': runInfrastructure(); break;
    default: throw new Error('Usage: node scripts/ci/run-component-checks.mjs <admin|website|backend|mobile|infrastructure> [--ci] [--app bingo-player|bingo-agent] [--flutter-version VERSION]');
  }
  console.log(`\n${component} checks passed.`);
} catch (error) {
  console.error(`\nFAILED COMPONENT: ${component || 'unknown'}\nCOMMAND / ERROR:\n${error.message}\nRERUN: node scripts/ci/run-component-checks.mjs ${process.argv.slice(2).join(' ')}`);
  process.exitCode = 1;
}
