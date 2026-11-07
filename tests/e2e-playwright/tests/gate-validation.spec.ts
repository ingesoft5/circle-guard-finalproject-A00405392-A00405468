import { expect, test } from '@playwright/test';
import { login, USERS, visibleText } from './helpers';

test.describe('Validación de acceso en la entrada', () => {
  test('el guardia valida el QR de un usuario sano y le da acceso', async ({ browser }) => {
    // 1. Un usuario abre su pase: la app pide a auth el QR firmado
    const userContext = await browser.newContext();
    const userPage = await userContext.newPage();
    const qrResponse = userPage.waitForResponse((r) => r.url().includes('/api/auth/api/v1/auth/qr/generate'));
    await login(userPage, USERS.admin);
    const { qrToken } = await (await qrResponse).json();
    expect(qrToken).toBeTruthy();
    await userContext.close();

    // 2. El guardia, en su propio navegador, escanea (pega) el código en la entrada
    const guardContext = await browser.newContext();
    const guardPage = await guardContext.newPage();
    await login(guardPage, USERS.guard);
    await visibleText(guardPage, 'Scan').click();
    await expect(visibleText(guardPage, 'GATE VALIDATION')).toBeVisible();

    const validation = guardPage.waitForResponse((r) => r.url().includes('/api/gateway/api/v1/gate/validate'));
    await guardPage.getByPlaceholder('QR code').fill(qrToken);
    await visibleText(guardPage, 'VALIDATE').click();

    expect((await (await validation).json()).status).toBe('GREEN');
    await expect(visibleText(guardPage, 'ACCESS GRANTED')).toBeVisible();
    await guardContext.close();
  });

  test('un código falso o alterado es rechazado', async ({ page }) => {
    await login(page, USERS.guard);
    await visibleText(page, 'Scan').click();

    const validation = page.waitForResponse((r) => r.url().includes('/api/gateway/api/v1/gate/validate'));
    await page.getByPlaceholder('QR code').fill('eyJhbGciOiJIUzI1NiJ9.codigo-falso.firma');
    await visibleText(page, 'VALIDATE').click();

    expect((await (await validation).json()).status).toBe('RED');
    await expect(visibleText(page, 'ACCESS DENIED')).toBeVisible();
    await expect(visibleText(page, 'INVALID OR EXPIRED TOKEN')).toBeVisible(); // la pantalla lo muestra en mayúsculas
  });
});
