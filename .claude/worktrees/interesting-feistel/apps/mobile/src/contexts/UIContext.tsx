import React, { createContext, useContext, useMemo } from 'react';
import { useUIStore } from '@/stores/uiStore';

export interface UIState {
  isMenuOpen: boolean;
  isTimersModalOpen: boolean;
  isReadinessModalOpen: boolean;
  searchQuery: string;
  activeSubTabs: Record<string, string>;
}

export interface UIDispatch {
  setIsMenuOpen: ReturnType<typeof useUIStore.getState>['setIsMenuOpen'];
  setIsTimersModalOpen: ReturnType<typeof useUIStore.getState>['setIsTimersModalOpen'];
  setIsReadinessModalOpen: ReturnType<typeof useUIStore.getState>['setIsReadinessModalOpen'];
}

const UIStateCtx = createContext<UIState | undefined>(undefined);
const UIDispatchCtx = createContext<UIDispatch | undefined>(undefined);

export const UIProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const {
    isMenuOpen,
    isTimersModalOpen,
    isReadinessModalOpen,
    setIsMenuOpen,
    setIsTimersModalOpen,
    setIsReadinessModalOpen,
  } = useUIStore((state) => state);

  const state = useMemo<UIState>(
    () => ({
      isMenuOpen,
      isTimersModalOpen,
      isReadinessModalOpen,
      searchQuery: '',
      activeSubTabs: {},
    }),
    [isMenuOpen, isTimersModalOpen, isReadinessModalOpen],
  );

  const dispatch = useMemo<UIDispatch>(
    () => ({
      setIsMenuOpen,
      setIsTimersModalOpen,
      setIsReadinessModalOpen,
    }),
    [setIsMenuOpen, setIsTimersModalOpen, setIsReadinessModalOpen],
  );

  return (
    <UIStateCtx.Provider value={state}>
      <UIDispatchCtx.Provider value={dispatch}>{children}</UIDispatchCtx.Provider>
    </UIStateCtx.Provider>
  );
};

export function useUIState() {
  const context = useContext(UIStateCtx);
  if (!context) {
    throw new Error('useUIState must be used within UIProvider');
  }
  return context;
}

export function useUIDispatch() {
  const context = useContext(UIDispatchCtx);
  if (!context) {
    throw new Error('useUIDispatch must be used within UIProvider');
  }
  return context;
}

