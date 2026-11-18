import assert from 'node:assert/strict';
import test from 'node:test';
import { openDevelopPullRequest } from './open-develop-pull-request.mjs';

const repository = 'equar/bingo-platform';
const pull = {
  state: 'open',
  html_url: 'https://github.com/equar/bingo-platform/pull/321',
  base: { ref: 'develop' },
  head: { ref: 'feature/example', repo: { full_name: repository } },
};

function mockFetch(routes) {
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push({ url: String(url), options });
    const response = await routes({ url: String(url), options, calls });
    return {
      ok: response.status >= 200 && response.status < 300,
      status: response.status,
      statusText: response.statusText || 'mock',
      text: async () => JSON.stringify(response.body ?? {}),
    };
  };
  return { fetchImpl, calls };
}

function options(overrides = {}) {
  return {
    fetchImpl: async () => { throw new Error('Unexpected GitHub API request'); },
    token: 'test-token',
    repository,
    ref: 'refs/heads/feature/example',
    log: () => {},
    ...overrides,
  };
}

test('eligible push creates one ready-for-review PR to develop', async () => {
  const { fetchImpl, calls } = mockFetch(({ url, options: request }) => {
    if (url.includes('/pulls?')) return { status: 200, body: [] };
    if (url.includes('/compare/')) return { status: 200, body: { ahead_by: 2 } };
    assert.equal(request.method, 'POST');
    const body = JSON.parse(request.body);
    assert.equal(body.base, 'develop');
    assert.equal(body.head, 'feature/example');
    assert.equal(body.draft, false);
    assert.match(body.title, /feature\/example/);
    return { status: 201, body: { html_url: pull.html_url } };
  });
  const result = await openDevelopPullRequest(options({ fetchImpl }));
  assert.equal(result.status, 'created');
  assert.equal(calls.filter((call) => call.options.method === 'POST').length, 1);
});

test('existing PR is logged and reused without a compare or create request', async () => {
  const { fetchImpl, calls } = mockFetch(() => ({ status: 200, body: [pull] }));
  const result = await openDevelopPullRequest(options({ fetchImpl }));
  assert.equal(result.status, 'existing');
  assert.equal(result.url, pull.html_url);
  assert.equal(calls.length, 1);
});

test('excluded promotion branches skip cleanly', async () => {
  for (const branch of ['develop', 'release', 'main']) {
    const result = await openDevelopPullRequest(options({ ref: `refs/heads/${branch}` }));
    assert.equal(result.status, 'excluded');
  }
});

test('deleted branches skip cleanly without API calls', async () => {
  const result = await openDevelopPullRequest(options({ deleted: true }));
  assert.equal(result.status, 'deleted');
});

test('branch with no commits to propose skips PR creation', async () => {
  const { fetchImpl, calls } = mockFetch(({ url }) => {
    if (url.includes('/pulls?')) return { status: 200, body: [] };
    return { status: 200, body: { ahead_by: 0, status: 'identical' } };
  });
  const result = await openDevelopPullRequest(options({ fetchImpl }));
  assert.equal(result.status, 'no-commits');
  assert.equal(calls.some((call) => call.options.method === 'POST'), false);
});

test('branch with commits but no net file changes skips PR creation', async () => {
  const { fetchImpl, calls } = mockFetch(({ url }) => {
    if (url.includes('/pulls?')) return { status: 200, body: [] };
    return { status: 200, body: { ahead_by: 2, files: [] } };
  });
  const result = await openDevelopPullRequest(options({ fetchImpl }));
  assert.equal(result.status, 'no-commits');
  assert.equal(calls.some((call) => call.options.method === 'POST'), false);
});

test('creation error is treated as success if another run created the PR', async () => {
  let pullLookups = 0;
  const { fetchImpl } = mockFetch(({ url, options: request }) => {
    if (url.includes('/pulls?')) {
      pullLookups += 1;
      return { status: 200, body: pullLookups === 1 ? [] : [pull] };
    }
    if (url.includes('/compare/')) return { status: 200, body: { ahead_by: 1 } };
    if (request.method === 'POST') return { status: 422, body: { message: 'pull request already exists' } };
    throw new Error(`Unexpected request ${url}`);
  });
  const result = await openDevelopPullRequest(options({ fetchImpl }));
  assert.equal(result.status, 'existing-after-create-race');
  assert.equal(result.url, pull.html_url);
});
