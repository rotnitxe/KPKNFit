import React from 'react';
import { TouchableOpacity } from 'react-native';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import GoalProjection from '../../components/analytics/GoalProjection';

describe('GoalProjection', () => {
  it('disables generation when offline to match the PWA guard', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <GoalProjection
            bodyProgress={[
              { date: '2025-03-01', weight: 80 },
              { date: '2025-03-08', weight: 79 },
            ]}
            settings={{
              userVitals: {
                targetWeight: 75,
                age: 30,
                weight: 80,
                height: 175,
                gender: 'male',
                activityLevel: 'moderate',
              },
            }}
            isOnline={false}
            nutritionLogs={[]}
          />
        </ThemeProvider>,
      );
    });

    const button = tree!.root.findByType(TouchableOpacity);
    expect(button.props.disabled).toBe(true);
    expect(JSON.stringify(tree!.toJSON())).toContain('Calcular Proyección IA');
  });
});
