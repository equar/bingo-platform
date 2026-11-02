import { describe, expect, it } from 'vitest';
import { getApiErrorReference, getFieldError, getUserFacingErrorMessage, pageDefaults, parseApiError } from './types';

describe('api types helpers', () => {
  it('builds a default empty page response', () => {
    const page = pageDefaults<string>();
    expect(page.content).toEqual([]);
    expect(page.first).toBe(true);
    expect(page.last).toBe(true);
  });

  it('maps stable error codes without displaying backend message text', () => {
    const error = parseApiError({
      response: {
        status: 409,
        data: { status: 409, message: 'SQL exception at internal host', code: 'INSUFFICIENT_BALANCE' },
      },
    });

    expect(error.status).toBe(409);
    expect(error.code).toBe('INSUFFICIENT_BALANCE');
    expect(error.message).toBe('You don’t have enough balance to complete this transaction.');
    expect(error.message).not.toContain('SQL');
  });

  it('uses status fallbacks for legacy and malformed responses', () => {
    const forbidden = parseApiError({ response: { status: 403, data: 'not-json' } });
    const missing = parseApiError({ response: { status: 404, data: { message: 'internal details' } } });

    expect(forbidden.message).toBe('You don’t have permission to perform this action.');
    expect(missing.message).toBe('This item is no longer available. Refresh and try again.');
    expect(missing.message).not.toContain('internal details');
  });

  it('maps login failures to a non-enumerating message', () => {
    const error = parseApiError({
      config: { url: '/api/v1/admin/login', method: 'post' },
      response: { status: 401, data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'account does not exist' } },
    });

    expect(error.message).toBe('The phone number or password is incorrect.');
    expect(error.message).not.toContain('account');
  });

  it('maps validation fields to safe inline messages', () => {
    const error = parseApiError({
      response: {
        status: 400,
        data: {
          code: 'VALIDATION_ERROR',
          details: [{ field: 'phoneNumber', message: 'SQL constraint failed' }],
        },
      },
    });

    expect(error.message).toBe('Check the highlighted fields and try again.');
    expect(getFieldError(error, 'phoneNumber')).toBe('Enter a valid phone number.');
  });

  it('maps PHONE_REQUIRED to a safe inline phone-field message', () => {
    const error = parseApiError({
      response: { status: 400, data: { code: 'PHONE_REQUIRED', message: 'Phone number is required' } },
    });

    expect(error.message).toBe('Enter a valid phone number.');
    expect(getFieldError(error, 'phoneNumber')).toBe('Enter a valid phone number.');
    expect(getFieldError(error, 'amount')).toBeUndefined();
  });

  it('does not expose raw error text from legacy or non-JSON responses', () => {
    expect(getUserFacingErrorMessage(new Error('java.sql.SQLException: internal host'))).toBe(
      'Something went wrong. Please try again.',
    );
    expect(getUserFacingErrorMessage({ response: { status: 500, data: '<html>stack trace</html>' } })).toBe(
      'Something went wrong. Please try again.',
    );
  });

  it('only displays correlation IDs in the safe reference format', () => {
    const safe = parseApiError({
      response: { status: 500, data: { correlationId: 'req_123-abc' } },
    });
    const unsafe = parseApiError({
      response: { status: 500, data: { correlationId: 'https://internal/token=secret' } },
    });

    expect(getApiErrorReference(safe)).toBe('Reference: req_123-abc');
    expect(getApiErrorReference(unsafe)).toBeUndefined();
  });

  it('distinguishes an uncertain financial network outcome from ordinary connection failures', () => {
    const payment = parseApiError({
      isAxiosError: true,
      code: 'ERR_NETWORK',
      config: { url: '/api/v1/player/wallet/transfers', method: 'post' },
    });
    const read = parseApiError({ isAxiosError: true, code: 'ERR_NETWORK', config: { url: '/api/v1/player/wallet', method: 'get' } });

    expect(payment.outcomeUnknown).toBe(true);
    expect(payment.message).toBe(
      'We couldn’t confirm the transaction status. Check your transaction history before trying again.',
    );
    expect(read.message).toBe('Unable to connect. Check your internet connection and try again.');
  });

  it('recognizes agent float top-up mutations as financial actions', () => {
    const error = parseApiError({
      isAxiosError: true,
      code: 'ERR_NETWORK',
      config: { url: '/api/v1/agent/float-top-up-requests', method: 'post' },
    });

    expect(error.outcomeUnknown).toBe(true);
    expect(error.message).toBe(
      'We couldn’t confirm the transaction status. Check your transaction history before trying again.',
    );
  });

  it('does not report normal cancellation as an error', () => {
    const error = parseApiError({ code: 'ERR_CANCELED', name: 'CanceledError' });

    expect(error.cancelled).toBe(true);
    expect(getUserFacingErrorMessage(error)).toBeUndefined();
  });
});
