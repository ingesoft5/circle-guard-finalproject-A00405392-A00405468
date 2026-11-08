import { expect, test } from '@playwright/test';
import { login, PASSWORD, USERS, visibleText } from './helpers';

test.describe('Inicio de sesión', () => {
  test('un usuario válido entra y ve su pase de acceso', async ({ page }) => {
    const loginResponse = page.waitForResponse((r) => r.url().includes('/api/auth/api/v1/auth/login'));

    await login(page, USERS.admin);

    const body = await (await loginResponse).json();
    expect(body.type).toBe('Bearer');
    expect(body.anonymousId).toMatch(/^[0-9a-f-]{36}$/);
    await expect(visibleText(page, /EXPIRES IN/)).toBeVisible();
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('una clave incorrecta muestra el error y no entra', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Username').fill(USERS.admin);
    await page.getByPlaceholder('Password').fill(`${PASSWORD}-mala`);

    const dialog = page.waitForEvent('dialog');
    await visibleText(page, 'SIGN IN').click();
    const alert = await dialog;

    expect(alert.message()).toContain('[401]');
    expect(alert.message()).toContain('Invalid username or password');
    await alert.dismiss();
    await expect(page).toHaveURL(/\/login/);
  });
});
