import { describe, expect, it } from 'vitest';
import { pageDefaults, parseApiError } from './types';

describe('api types helpers', () => {
  it('builds a default empty page response', () => {
    const page = pageDefaults<string>();
    expect(page.content).toEqual([]);
    expect(page.first).toBe(true);
    expect(page.last).toBe(true);
  });

  it('parses api error shapes', () => {
    const error = parseApiError({ response: { status: 409, data: { status: 409, message: 'conflict', code: 'CONFLICT' } } });
    expect(error.status).toBe(409);
    expect(error.message).toBe('conflict');
    expect(error.code).toBe('CONFLICT');
  });
});
