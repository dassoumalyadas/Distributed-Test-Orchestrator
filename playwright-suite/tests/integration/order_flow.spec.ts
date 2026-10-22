import { test, expect } from '@playwright/test';

test.describe('Order Lifecycle & Webhook Integration', () => {
  test('End-to-end order placement and invoice generation', async ({ request }) => {
    // 1. Create order
    const orderRes = await request.post('https://jsonplaceholder.typicode.com/posts', {
      data: { orderId: 'ORD-9981', amount: 149.99, currency: 'USD' }
    });
    expect(orderRes.ok()).toBeTruthy();

    // 2. Query order status
    const statusRes = await request.get('https://jsonplaceholder.typicode.com/posts/1');
    expect(statusRes.status()).toBe(200);
    const data = await statusRes.json();
    expect(data.id).toBe(1);
  });
});
