jest.mock('../nutritionAnalyzer', () => ({
  analyzeNutritionDraft: jest.fn(async () => ({ items: [{ calories: 100 }], requiresReview: false })),
}));

jest.mock('../coachChatService', () => ({
  buildCoachContextSnapshot: jest.fn(() => ({ readiness: 7 })),
  generateCoachReply: jest.fn(() => ({ reply: 'ok' })),
  summarizeConversationTitle: jest.fn(() => 'Resumen'),
}));

import {
  analyzeNutritionDraft,
  buildCoachContextSnapshot,
  generateCoachReply,
  generateWeightProjection,
  summarizeConversationTitle,
} from '../aiService';

describe('aiService', () => {
  it('delegates nutrition analysis to the configured runtime', async () => {
    const result = await analyzeNutritionDraft({ description: 'arroz', knownFoods: [], locale: 'es-CL', schemaVersion: 'rn-v1' } as any);
    expect(result).toEqual({ items: [{ calories: 100 }], requiresReview: false });
  });

  it('delegates coach helpers through a single service entrypoint', () => {
    expect(buildCoachContextSnapshot(null, [], [], null)).toEqual({ readiness: 7 });
    expect(generateCoachReply({ userText: 'hola', context: {} as any, recentMessages: [] }).reply).toBe('ok');
    expect(summarizeConversationTitle('hola')).toBe('Resumen');
  });

  it('provides a weight projection contract for GoalProjection', async () => {
    await expect(
      generateWeightProjection(
        2200,
        2500,
        [
          { date: '2025-03-01', weight: 80 },
          { date: '2025-03-08', weight: 79.2 },
        ],
        75,
      ),
    ).resolves.toEqual({
      projection: '6 semanas',
      summary: 'Tu consumo calórico es adecuado. Mantén el ritmo actual.',
    });
  });
});
