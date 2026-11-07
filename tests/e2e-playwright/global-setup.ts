import { request, type FullConfig } from '@playwright/test';

/**
 * Datos mínimos para los E2E: un cuestionario de salud activo.
 * Es idempotente: si el ambiente ya tiene uno activo, no crea nada.
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0].use.baseURL as string;
  const api = await request.newContext({ baseURL });
  try {
    const active = await api.get('/api/form/api/v1/questionnaires/active');
    if (active.ok()) return;

    const created = await api.post('/api/form/api/v1/questionnaires', {
      data: {
        title: 'Autoevaluación de salud',
        description: 'Responde antes de venir al campus',
        version: 1,
        isActive: false,
        questions: [
          { text: '¿Tienes fiebre (38 °C o más)?', type: 'YES_NO', orderIndex: 1 },
          { text: '¿Tienes tos o dificultad para respirar?', type: 'YES_NO', orderIndex: 2 },
        ],
      },
    });
    if (!created.ok()) {
      throw new Error(`No se pudo crear el cuestionario: HTTP ${created.status()} ${await created.text()}`);
    }
    const { id } = await created.json();
    const activated = await api.post(`/api/form/api/v1/questionnaires/${id}/activate`);
    if (!activated.ok()) {
      throw new Error(`No se pudo activar el cuestionario: HTTP ${activated.status()}`);
    }
  } finally {
    await api.dispose();
  }
}
