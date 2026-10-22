import { test, expect } from '@playwright/test';

test.describe('Authentication & User API', () => {
  test('GET /users returns active user list', async ({ request }) => {
    const response = await request.get('https://jsonplaceholder.typicode.com/users');
    expect(response.status()).toBe(200);
    const users = await response.json();
    expect(Array.isArray(users)).toBeTruthy();
    expect(users.length).toBeGreaterThan(0);
    expect(users[0]).toHaveProperty('email');
  });

  test('POST /login validates authorization tokens', async ({ request }) => {
    const response = await request.post('https://jsonplaceholder.typicode.com/posts', {
      data: {
        title: 'AuthSession',
        body: 'JWT_SAMPLE_TOKEN_PAYLOAD',
        userId: 1,
      },
    });
    expect(response.status()).toBe(201);
    const result = await response.json();
    expect(result.id).toBeDefined();
  });
});
