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
  generateExercisesForPurpose,
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

  it('returns purpose-based exercise suggestions for TrainingPurpose', async () => {
    const response = await generateExercisesForPurpose('quiero mejorar sprint y salto', {
      exerciseCatalog: [
        {
          id: '1',
          name: 'Sentadilla frontal',
          description: '',
          involvedMuscles: [
            { muscle: 'quadriceps', role: 'primary' },
            { muscle: 'glutes', role: 'secondary' },
          ],
          category: 'Fuerza',
          type: 'Basico',
          equipment: 'Barra',
          force: 'Sentadilla',
        } as any,
        {
          id: '2',
          name: 'Press banca',
          description: '',
          involvedMuscles: [{ muscle: 'chest', role: 'primary' }],
          category: 'Fuerza',
          type: 'Basico',
          equipment: 'Barra',
          force: 'Empuje',
        } as any,
      ],
    });

    expect(response.exercises.length).toBeGreaterThan(0);
    expect(response.exercises[0]?.name).toBe('Sentadilla frontal');
  });
});
