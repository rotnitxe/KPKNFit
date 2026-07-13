import { generateCoachBriefing } from '../../services/coachChatService';

describe('coachChatService', () => {
  it('generates a briefing with the core context signals', () => {
    const briefing = generateCoachBriefing({
      activeProgramName: 'Upper Push',
      weeklySessionCount: 3,
      completedSetsThisWeek: 28,
      plannedSetsThisWeek: 40,
      latestWeight: 82.4,
      latestBodyFat: 14.2,
      todayCalories: 2050,
      todayProtein: 132,
      readiness: 5,
    });

    expect(briefing).toContain('Upper Push');
    expect(briefing).toContain('Readiness');
    expect(briefing).toContain('2050 kcal');
    expect(briefing).toContain('Prioridad inmediata');
  });
});

