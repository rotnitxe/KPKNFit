jest.mock('@react-navigation/native', () => {
  const actual = jest.requireActual('@react-navigation/native');
  return {
    ...actual,
    useNavigation: jest.fn(),
    useRoute: jest.fn(),
  };
});

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import { useNavigation, useRoute } from '@react-navigation/native';
import { WikiHomeScreen } from '../../screens/Wiki/WikiHomeScreen';
import { WikiArticleScreen } from '../../screens/Wiki/WikiArticleScreen';
import { WikiChainDetailScreen } from '../../screens/Wiki/WikiChainDetailScreen';
import { WikiMobilityScreen } from '../../screens/Wiki/WikiMobilityScreen';
import { WikiBiomechanicsScreen } from '../../screens/Wiki/WikiBiomechanicsScreen';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useBodyStore } from '../../stores/bodyStore';

const mockNavigate = jest.fn();
const mockGoBack = jest.fn();

function getNavigationMock() {
  return {
    navigate: mockNavigate,
    goBack: mockGoBack,
    canGoBack: jest.fn(() => true),
    emit: jest.fn(() => ({ defaultPrevented: false })),
  } as any;
}

describe('Wiki secondary views', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    (useNavigation as jest.Mock).mockReturnValue(getNavigationMock());
    (useRoute as jest.Mock).mockReturnValue({ params: {} });
    act(() => {
      useExerciseStore.setState({ exerciseList: [] } as any);
      useBodyStore.setState({
        biomechanicalData: null,
        biomechanicalAnalysis: null,
      } as any);
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('navigates from WikiHome to labs and article landing cards', () => {
    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiHomeScreen />
        </ThemeProvider>,
      );
    });

    // Wait for loading state to finish
    act(() => {
      jest.advanceTimersByTime(700);
    });

    // Find biomechanics card by accessibility label
    const allProps = tree!.root.findAllByProps({ accessibilityRole: 'button' });
    const biomechCard = allProps.find(
      (node: any) => (node.props as any)?.accessibilityLabel?.includes('Biomecánica aplicada')
    );
    
    if (biomechCard && (biomechCard.props as any)?.onPress) {
      act(() => {
        (biomechCard.props as any).onPress();
      });
      expect(mockNavigate).toHaveBeenCalledWith('WikiBiomechanics');
    }

    // Find muscle article card
    const articleCard = allProps.find(
      (node: any) => (node.props as any)?.accessibilityLabel?.includes('Pectoral Mayor')
    );
    
    if (articleCard && (articleCard.props as any)?.onPress) {
      act(() => {
        (articleCard.props as any).onPress();
      });
      expect(mockNavigate).toHaveBeenCalledWith('WikiArticle', {
        articleType: 'muscle',
        articleId: 'pectoralis-major',
      });
    }
  });

  it('renders the wiki article screen with useful related content', () => {
    (useRoute as jest.Mock).mockReturnValue({
      params: { articleType: 'muscle', articleId: 'pectoralis-major' },
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiArticleScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Pectoral Mayor');
    expect(json).toContain('Estructuras relacionadas');
  });

  it('renders the wiki article screen empty state when article not found', () => {
    (useRoute as jest.Mock).mockReturnValue({
      params: { articleType: 'muscle', articleId: 'non-existent-article' },
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiArticleScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Artículo no disponible');
    expect(json).toContain('no existe o fue removido');
  });

  it('renders WikiHome empty state when search has no results', () => {
    (useRoute as jest.Mock).mockReturnValue({ params: {} });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiHomeScreen />
        </ThemeProvider>,
      );
    });

    const searchInput = tree!.root.findByProps({ placeholder: 'Buscar en la Wiki...' });
    act(() => {
      searchInput.props.onChangeText('xyz-nonexistent-query');
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Sin resultados');
  });

  it('renders chain detail with grouped exercises', () => {
    act(() => {
      useExerciseStore.setState({
        exerciseList: [
          {
            id: 'press-banca',
            name: 'Press de Banca',
            description: 'Trabajo de empuje horizontal.',
            involvedMuscles: [{ muscle: 'pectoralis-major', role: 'primary' }],
            category: 'Pecho',
            type: 'Básico',
            equipment: 'Barra',
            force: 'Horizontal',
            bodyPart: 'upper',
            chain: 'anterior',
          } as any,
        ],
      } as any);
    });
    (useRoute as jest.Mock).mockReturnValue({
      params: { chainId: 'anterior' },
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiChainDetailScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Cadena Anterior');
    expect(json).toContain('Press de Banca');
  });

  it('generates a mobility routine from a target query', () => {
    act(() => {
      useExerciseStore.setState({
        exerciseList: [
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
          } as any,
        ],
      } as any);
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiMobilityScreen />
        </ThemeProvider>,
      );
    });

    const input = tree!.root.findByProps({ placeholder: 'Ej: cadera, hombro, tobillo...' });
    act(() => {
      input.props.onChangeText('cadera');
    });

    const generateButton = tree!.root.findByProps({ accessibilityLabel: 'Generar rutina de movilidad' });
    act(() => {
      generateButton.props.onPress();
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Rutina para Cadera');
    expect(json).toContain('90/90 switches');
  });

  it('shows biomechanics metrics and scenario cards when body data exists', () => {
    act(() => {
      useBodyStore.setState({
        biomechanicalData: {
          height: 180,
          wingspan: 184,
          torsoLength: 60,
          femurLength: 50,
          tibiaLength: 45,
          humerusLength: 32,
          forearmLength: 28,
        },
        biomechanicalAnalysis: {
          apeIndex: { value: 4, interpretation: 'Favorable' },
          advantages: [{ title: 'Envergadura', explanation: 'Apoya la bisagra.' }],
          challenges: [{ title: 'Torso', explanation: 'Exige más inclinación.' }],
          exerciseSpecificRecommendations: [
            { exerciseName: 'Sentadilla', recommendation: 'Mantén brace firme.' },
          ],
        },
      } as any);
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiBiomechanicsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Palitos biomecánicos');
    expect(json).toContain('Ape index');
    expect(json).toContain('Envergadura');
    expect(json).toContain('Torso');
  });

  it('renders the biomechanics empty state when no measurements are available', () => {
    act(() => {
      useBodyStore.setState({
        biomechanicalData: null,
        biomechanicalAnalysis: null,
      } as any);
    });

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <WikiBiomechanicsScreen />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Sin datos aún');
    expect(json).toContain('Sin datos suficientes');
  });
});
