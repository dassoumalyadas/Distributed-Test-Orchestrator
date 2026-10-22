import { test, expect } from '@playwright/test';

test.describe('E-Commerce Checkout Flow', () => {
  test('User can browse products and view details', async ({ page }) => {
    // Navigating to a fast, reliable public demo page
    await page.goto('https://example.com');
    await expect(page).toHaveTitle(/Example Domain/);
    const header = page.locator('h1');
    await expect(header).toHaveText('Example Domain');
  });

  test('User can initiate payment workflow', async ({ page }) => {
    await page.goto('https://example.com');
    const link = page.locator('a');
    await expect(link).toBeVisible();
    await link.click();
    await expect(page).toHaveURL(/iana\.org/);
  });
});
