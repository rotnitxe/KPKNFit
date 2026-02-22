// contexts/UIContext.tsx
// Extracted UI state (modals, navigation chrome, search) from AppContext
// to prevent modal opens/closes from re-rendering data-heavy components.

import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';

export interface UIState {
    isBodyLogModalOpen: boolean;
    isNutritionLogModalOpen: boolean;
    isMeasurementsModalOpen: boolean;
    isStartWorkoutModalOpen: boolean;
    isCustomExerciseEditorOpen: boolean;
    isFoodEditorOpen: boolean;
    isFinishModalOpen: boolean;
    isTimeSaverModalOpen: boolean;
    isTimersModalOpen: boolean;
    isReadinessModalOpen: boolean;
    isAddToPlaylistSheetOpen: boolean;
    isWorkoutEditorOpen: boolean;
    isMuscleListEditorOpen: boolean;
    isLiveCoachActive: boolean;
    isLogActionSheetOpen: boolean;
    isWorkoutExitModalOpen: boolean;
    isAddPantryItemModalOpen: boolean;
    isSpecialSessionModalOpen: boolean;
    isMenuOpen: boolean;
    isGlobalVoiceActive: boolean;
    searchQuery: string;
    activeSubTabs: Record<string, string>;
}

export interface UIDispatch {
    setIsBodyLogModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsNutritionLogModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsMeasurementsModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsStartWorkoutModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsFinishModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsTimeSaverModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsTimersModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsReadinessModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsAddToPlaylistSheetOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsLiveCoachActive: React.Dispatch<React.SetStateAction<boolean>>;
    setIsLogActionSheetOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsWorkoutExitModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsWorkoutEditorOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsMenuOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsGlobalVoiceActive: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSpecialSessionModalOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setSearchQuery: React.Dispatch<React.SetStateAction<string>>;
    setActiveSubTabs: React.Dispatch<React.SetStateAction<Record<string, string>>>;
}

const UIStateCtx = createContext<UIState | undefined>(undefined);
const UIDispatchCtx = createContext<UIDispatch | undefined>(undefined);

export const UIProvider: React.FC<{
    children: React.ReactNode;
    overrides: {
        states: UIState;
        dispatches: UIDispatch;
    };
}> = ({ children, overrides }) => {
    return (
        <UIStateCtx.Provider value={overrides.states}>
            <UIDispatchCtx.Provider value={overrides.dispatches}>
                {children}
            </UIDispatchCtx.Provider>
        </UIStateCtx.Provider>
    );
};

export const useUIState = (): UIState => {
    const ctx = useContext(UIStateCtx);
    if (!ctx) throw new Error('useUIState must be used within UIProvider');
    return ctx;
};

export const useUIDispatch = (): UIDispatch => {
    const ctx = useContext(UIDispatchCtx);
    if (!ctx) throw new Error('useUIDispatch must be used within UIProvider');
    return ctx;
};
