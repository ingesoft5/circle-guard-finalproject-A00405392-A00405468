import { expect, test } from '@playwright/test';

test.describe('Versión desplegada', () => {
  test('el web expone la versión que corre en el ambiente en /version', async ({ request }) => {
    const res = await request.get('/version');

    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('text/plain');
    // En el pipeline es la versión semántica desplegada (p. ej. 1.1.0); en local, el IMAGE_TAG (p. ej. dev).
    const version = (await res.text()).trim();
    expect(version).toMatch(/^[0-9A-Za-z][0-9A-Za-z._-]*$/);
  });
});
