import { expect, type Locator, type Page } from '@playwright/test';

/** Usuarios de prueba de la migración V2 de auth (clave "password"). */
export const USERS = {
  admin: 'super_admin',
  health: 'health_user',
  guard: 'staff_guard',
} as const;

export const PASSWORD = 'password';

/**
 * Texto exacto y visible. expo-router deja montadas pantallas ocultas (p. ej. el inicio detrás de otra
 * ruta), así que un mismo texto puede existir dos veces en el DOM; solo interesa el que se ve.
 */
export function visibleText(page: Page, text: string | RegExp): Locator {
  const selector = typeof text === 'string' ? `text="${text}"` : `text=${text}`;
  return page.locator(`${selector} >> visible=true`).first();
}

/** Inicia sesión desde la pantalla de login y espera a llegar al inicio. */
export async function login(page: Page, username: string, password = PASSWORD) {
  await page.goto('/login');
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill(password);
  await visibleText(page, 'SIGN IN').click();
  await expect(visibleText(page, 'STATUS')).toBeVisible();
}
