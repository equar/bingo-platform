import { expect, test } from '@playwright/test';

const email = process.env.BINGO_E2E_ADMIN_EMAIL ?? '';
const password = process.env.BINGO_E2E_ADMIN_PASSWORD ?? '';

test('authorized Admin can sign in and open the dashboard', async ({ page }) => {
  test.skip(!email || !password, 'Disposable Admin E2E credentials are not configured.');

  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
});
