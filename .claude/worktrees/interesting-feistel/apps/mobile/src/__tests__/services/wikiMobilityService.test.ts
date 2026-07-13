import { buildMobilityRoutine, buildMobilitySuggestions } from '../../services/wikiMobilityService';

describe('wikiMobilityService', () => {
  const exerciseList = [
    {
      id: 'squat',
      name: 'Sentadilla Trasera',
      description: 'Trabajo de pierna.',
      involvedMuscles: [{ muscle: 'quadriceps', role: 'primary' }],
      category: 'Pierna',
      type: 'Básico',
      equipment: 'Barra',
      force: 'Vertical',
      bodyPart: 'lower',
      chain: 'anterior',
    },
    {
      id: 'press',
      name: 'Press Militar',
      description: 'Empuje vertical.',
      involvedMuscles: [{ muscle: 'deltoid', role: 'primary' }],
      category: 'Hombro',
      type: 'Básico',
      equipment: 'Barra',
      force: 'Vertical',
      bodyPart: 'upper',
      chain: 'anterior',
    },
  ] as any[];

  it('returns suggestions for a mobility target', () => {
    const suggestions = buildMobilitySuggestions('cadera', exerciseList);

    expect(suggestions).toContain('Cadera');
  });

  it('builds a hip routine with a detail link and total duration', () => {
    const routine = buildMobilityRoutine('cadera', exerciseList);

    expect(routine.targetLabel).toBe('Cadera');
    expect(routine.detailLink?.articleType).toBe('joint');
    expect(routine.detailLink?.articleId).toBe('hip');
    expect(routine.totalDurationSeconds).toBeGreaterThan(0);
    expect(routine.steps.length).toBeGreaterThan(0);
  });

  it('falls back to a general routine when the query is unknown', () => {
    const routine = buildMobilityRoutine('texto sin match', exerciseList);

    expect(routine.targetLabel).toBe('General');
    expect(routine.steps.length).toBeGreaterThan(0);
  });
});

