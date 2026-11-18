import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const API_ROOT = 'https://api.github.com';

function apiError(response, detail) {
  return new Error(`GitHub API ${response.status} ${response.statusText}: ${detail || 'request failed'}`);
}

async function requestJson(fetchImpl, token, path, options = {}) {
  const response = await fetchImpl(`${API_ROOT}${path}`, {
    ...options,
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': '2022-11-28',
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : {};
  } catch {
    payload = { message: text };
  }
  if (!response.ok) throw apiError(response, payload.message);
  return payload;
}

function openPullsPath(owner, repo, branch) {
  const params = new URLSearchParams({
    state: 'open',
    head: `${owner}:${branch}`,
    base: 'develop',
    per_page: '100',
  });
  return `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/pulls?${params}`;
}

async function findOpenPullRequest(fetchImpl, token, owner, repo, branch) {
  const pulls = await requestJson(fetchImpl, token, openPullsPath(owner, repo, branch));
  return pulls.find((pull) =>
    pull.state === 'open' &&
    pull.base?.ref === 'develop' &&
    pull.head?.ref === branch &&
    pull.head?.repo?.full_name === `${owner}/${repo}`,
  );
}

export async function openDevelopPullRequest({
  fetchImpl = fetch,
  token,
  repository,
  ref,
  deleted = false,
  log = console.log,
}) {
  const [owner, repo, ...extra] = (repository || '').split('/');
  const prefix = 'refs/heads/';
  const branch = ref?.startsWith(prefix) ? ref.slice(prefix.length) : '';

  if (!owner || !repo || extra.length || !token) {
    throw new Error('Missing GitHub repository or App token configuration.');
  }
  if (deleted) {
    log('Skipping deleted branch push.');
    return { status: 'deleted' };
  }
  if (!branch) {
    log('Skipping event that is not a branch push.');
    return { status: 'not-a-branch' };
  }
  if (['develop', 'release', 'main'].includes(branch)) {
    log(`Skipping protected promotion branch ${branch}.`);
    return { status: 'excluded' };
  }

  const existing = await findOpenPullRequest(fetchImpl, token, owner, repo, branch);
  if (existing) {
    log(`An open PR already exists: ${existing.html_url}`);
    return { status: 'existing', url: existing.html_url };
  }

  const compare = `develop...${branch}`;
  const comparison = await requestJson(
    fetchImpl,
    token,
    `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/compare/${encodeURIComponent(compare)}`,
  );
  const hasNoNetChanges = Array.isArray(comparison.files) && comparison.files.length === 0;
  if (!Number.isInteger(comparison.ahead_by) || comparison.ahead_by <= 0 || hasNoNetChanges) {
    log(`No commits to propose from ${branch} into develop; skipping PR creation.`);
    return { status: 'no-commits' };
  }

  const safeBranch = branch.replaceAll('`', "'");
  const title = `Promote ${branch} to develop`.slice(0, 256);
  const body = [
    `Automated pull request for changes pushed to \`${safeBranch}\`.`,
    '',
    'Please review the changes and the normal PR validation results.',
    'This workflow opens pull requests only; it does not approve or merge them.',
  ].join('\n');

  try {
    const created = await requestJson(
      fetchImpl,
      token,
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/pulls`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, head: branch, base: 'develop', body, draft: false }),
      },
    );
    log(`Opened PR: ${created.html_url}`);
    return { status: 'created', url: created.html_url };
  } catch (createError) {
    let raced;
    try {
      raced = await findOpenPullRequest(fetchImpl, token, owner, repo, branch);
    } catch (lookupError) {
      throw new Error(
        `PR creation failed (${createError.message}); checking for a concurrent PR also failed (${lookupError.message}).`,
      );
    }
    if (raced) {
      log(`Another run created the PR: ${raced.html_url}`);
      return { status: 'existing-after-create-race', url: raced.html_url };
    }
    throw new Error(`PR creation failed: ${createError.message}`);
  }
}

async function main() {
  let event;
  try {
    event = JSON.parse(await (await import('node:fs/promises')).readFile(process.env.GITHUB_EVENT_PATH, 'utf8'));
  } catch {
    throw new Error('Could not read the GitHub push event payload.');
  }
  await openDevelopPullRequest({
    token: process.env.GH_TOKEN,
    repository: process.env.GITHUB_REPOSITORY,
    ref: process.env.GITHUB_REF,
    deleted: event.deleted === true,
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(`Failed to open working branch PR: ${error.message}`);
    process.exitCode = 1;
  });
}
