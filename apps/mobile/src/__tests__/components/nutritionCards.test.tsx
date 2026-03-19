import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import GoalReachedModal from '../../components/nutrition/GoalReachedModal';
import { NutritionRecentLogsCard } from '../../components/nutrition/NutritionRecentLogsCard';

describe('nutrition UI states', () => {
  it('renders the empty recent logs state', () => {
    let tree: renderer.ReactTestRenderer | null = null;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <NutritionRecentLogsCard logs={[]} />
        </ThemeProvider>,
      );
    });

    expect(JSON.stringify(tree?.toJSON())).toContain('Aún no hay registros guardados.');
  });

  it('renders the goal reached modal when visible and hides it when closed', () => {
    let visibleTree: renderer.ReactTestRenderer | null = null;
    act(() => {
      visibleTree = renderer.create(
        <ThemeProvider initialDark={false}>
          <GoalReachedModal
            visible
            onClose={jest.fn()}
            calories={2300}
            target={2200}
            protein={180}
            carbs={220}
            fats={70}
          />
        </ThemeProvider>,
      );
    });

    expect(JSON.stringify(visibleTree?.toJSON())).toContain('¡Meta Alcanzada!');

    let hiddenTree: renderer.ReactTestRenderer | null = null;
    act(() => {
      hiddenTree = renderer.create(
        <ThemeProvider initialDark={false}>
          <GoalReachedModal
            visible={false}
            onClose={jest.fn()}
            calories={2300}
            target={2200}
            protein={180}
            carbs={220}
            fats={70}
          />
        </ThemeProvider>,
      );
    });

    expect(hiddenTree?.toJSON()).toBeNull();
  });
});
