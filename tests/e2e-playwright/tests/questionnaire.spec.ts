import { expect, test } from '@playwright/test';
import { login, USERS, visibleText } from './helpers';

test.describe('Cuestionario de salud', () => {
  test('el usuario responde el cuestionario y su reporte llega a form', async ({ page }) => {
    await login(page, USERS.health);

    await visibleText(page, 'REPORT').click();
    await expect(visibleText(page, 'Health Screening')).toBeVisible();

    // Respuestas: SÍ a la primera pregunta, NO a la segunda
    await visibleText(page, 'YES').click();
    await page.locator('text="NO" >> visible=true').nth(1).click();

    const submitted = page.waitForResponse(
      (r) => r.url().includes('/api/form/api/v1/surveys') && r.request().method() === 'POST',
    );
    await visibleText(page, 'SUBMIT REPORT').click();
    const response = await submitted;

    expect(response.status()).toBe(200);
    const sent = response.request().postDataJSON();
    expect(Object.values(sent.responses)).toEqual(expect.arrayContaining(['YES', 'NO']));
    const saved = await response.json();
    expect(saved.id).toBeTruthy();
    expect(saved.anonymousId).toBe(sent.anonymousId);

    // Vuelve al inicio con su pase de acceso
    await expect(visibleText(page, 'STATUS')).toBeVisible();
  });
});
