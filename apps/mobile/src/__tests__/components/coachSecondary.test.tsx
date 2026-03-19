jest.mock('../../components/ui/TacticalModal', () => {
  return {
    __esModule: true,
    default: ({ children, isOpen }: { children: React.ReactNode; isOpen: boolean }) =>
      (isOpen ? children : null),
  };
});

import React from 'react';
import renderer, { act } from 'react-test-renderer';
import { ThemeProvider } from '../../theme';
import { CoachConversationList } from '../../components/coach/CoachConversationList';
import { CoachMessageBubble } from '../../components/coach/CoachMessageBubble';
import { CoachBriefingDrawer } from '../../components/coach/CoachBriefingDrawer';

describe('Coach secondary surfaces', () => {
  it('allows selecting and deleting a conversation', () => {
    const onSelect = jest.fn();
    const onDelete = jest.fn();

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <CoachConversationList
            conversations={[
              {
                id: 'conv-1',
                title: 'Consulta Nutrición',
                createdAt: '2025-01-01T12:00:00.000Z',
                updatedAt: '2025-01-01T12:00:00.000Z',
                messages: [],
              },
              {
                id: 'conv-2',
                title: 'Recuperación',
                createdAt: '2025-01-02T12:00:00.000Z',
                updatedAt: '2025-01-02T12:00:00.000Z',
                messages: [],
              },
            ]}
            activeConversationId="conv-2"
            onSelect={onSelect}
            onDelete={onDelete}
          />
        </ThemeProvider>,
      );
    });

    const openConversation = tree!.root.findByProps({ accessibilityLabel: 'Abrir Consulta Nutrición' });
    act(() => {
      openConversation.props.onPress();
    });
    expect(onSelect).toHaveBeenCalledWith('conv-1');

    const deleteConversation = tree!.root.findByProps({ accessibilityLabel: 'Eliminar Consulta Nutrición' });
    act(() => {
      deleteConversation.props.onPress();
    });
    expect(onDelete).toHaveBeenCalledWith('conv-1');
  });

  it('renders coach messages with accessible content', () => {
    const timeSpy = jest.spyOn(Date.prototype, 'toLocaleTimeString').mockReturnValue('12:30');
    try {
      let tree: renderer.ReactTestRenderer;
      act(() => {
        tree = renderer.create(
          <ThemeProvider initialDark={false}>
            <CoachMessageBubble
              message={{
                id: 'msg-1',
                role: 'assistant',
                text: 'Tu readiness está bien.',
                createdAt: '2025-01-01T12:30:00.000Z',
              }}
            />
          </ThemeProvider>,
        );
      });

      const json = JSON.stringify(tree!.toJSON());
      expect(json).toContain('Tu readiness está bien.');
      expect(json).toContain('12:30');
    } finally {
      timeSpy.mockRestore();
    }
  });

  it('shows the briefing drawer and closes it', () => {
    const onClose = jest.fn();

    let tree: renderer.ReactTestRenderer;
    act(() => {
      tree = renderer.create(
        <ThemeProvider initialDark={false}>
          <CoachBriefingDrawer
            isOpen
            onClose={onClose}
            briefing={'Programa activo: Upper\nReadiness: 7/10'}
          />
        </ThemeProvider>,
      );
    });

    const json = JSON.stringify(tree!.toJSON());
    expect(json).toContain('Informe de tu Coach IA');
    expect(json).toContain('Readiness: 7/10');

    const closeButton = tree!.root.findByProps({ accessibilityLabel: 'Cerrar briefing' });
    act(() => {
      closeButton.props.onPress();
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
