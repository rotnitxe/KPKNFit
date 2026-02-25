import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import type {
    View, Session, Program, ExerciseMuscleInfo, FoodItem,
    CustomExerciseModalData, SessionBackground, ToastData,
    OngoingWorkoutState
} from '../types';

interface UIStoreState {
    view: View;
    historyStack: { view: View; data?: any }[];

    // Modal states
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
    specialSessionData: any | null;
    isMenuOpen: boolean;
    isGlobalVoiceActive: boolean;

    // Viewing/editing IDs
    activeProgramId: string | null;
    editingProgramId: string | null;
    editingSessionInfo: { programId: string; macroIndex: number; mesoIndex: number; weekId: string; sessionId?: string; dayOfWeek?: number } | null;
    loggingSessionInfo: { programId: string; sessionId: string } | null;
    viewingSessionInfo: { programId: string; sessionId: string } | null;
    activeSession: Session | null;
    viewingExerciseId: string | null;
    viewingFoodId: string | null;
    viewingMuscleGroupId: string | null;
    viewingJointId: string | null;
    viewingTendonId: string | null;
    viewingMovementPatternId: string | null;
    viewingBodyPartId: string | null;
    viewingChainId: string | null;
    viewingMuscleCategoryName: string | null;
    exerciseToAddId: string | null;
    editingCustomExerciseData: CustomExerciseModalData | null;
    editingFoodData: { food?: FoodItem; preFilledName?: string } | null;
    pendingWorkoutForReadinessCheck: { session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D'; location?: { macroIndex: number; mesoIndex: number; weekId: string } } | null;
    editingWorkoutSessionInfo: { session: Session; programId: string; macroIndex: number; mesoIndex: number; weekId: string } | null;
    editingCategoryInfo: { name: string; type: 'bodyPart' | 'special' } | null;
    foodItemToAdd_to_pantry: FoodItem | null;
    pendingNavigation: any | null;

    // Triggers
    saveSessionTrigger: number;
    addExerciseTrigger: number;
    saveProgramTrigger: number;
    saveLoggedWorkoutTrigger: number;
    modifyWorkoutTrigger: number;

    // Search & tabs
    searchQuery: string;
    activeSubTabs: Record<string, string>;

    // Misc transient state
    currentBackgroundOverride: SessionBackground | undefined;
    restTimer: { duration: number; remaining: number; key: number; exerciseName: string; endTime: number } | null;
    isDirty: boolean;
    kpknAction: any;
    pendingCoachBriefing: string | null;
    pendingWorkoutAfterBriefing: { session: Session; program: Program; weekVariant?: 'A' | 'B' | 'C' | 'D'; location?: { macroIndex: number; mesoIndex: number; weekId: string } } | null;

    // Runtime state
    isOnline: boolean;
    installPromptEvent: any;
    toasts: ToastData[];
    onExerciseCreated: ((exercise: ExerciseMuscleInfo) => void) | null;

    // Actions (all are simple setters)
    setView: (view: View) => void;
    setHistoryStack: (updater: { view: View; data?: any }[] | ((prev: { view: View; data?: any }[]) => { view: View; data?: any }[])) => void;
    setIsBodyLogModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsNutritionLogModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsMeasurementsModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsStartWorkoutModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsCustomExerciseEditorOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsFoodEditorOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsFinishModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsTimeSaverModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsTimersModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsReadinessModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsAddToPlaylistSheetOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsWorkoutEditorOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsMuscleListEditorOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsLiveCoachActive: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsLogActionSheetOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsWorkoutExitModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsAddPantryItemModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsSpecialSessionModalOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setSpecialSessionData: (v: any) => void;
    setIsMenuOpen: (v: boolean | ((p: boolean) => boolean)) => void;
    setIsGlobalVoiceActive: (v: boolean | ((p: boolean) => boolean)) => void;
    setActiveProgramId: (v: string | null) => void;
    setEditingProgramId: (v: string | null) => void;
    setEditingSessionInfo: (v: UIStoreState['editingSessionInfo']) => void;
    setLoggingSessionInfo: (v: UIStoreState['loggingSessionInfo']) => void;
    setViewingSessionInfo: (v: UIStoreState['viewingSessionInfo']) => void;
    setActiveSession: (v: Session | null) => void;
    setViewingExerciseId: (v: string | null) => void;
    setViewingFoodId: (v: string | null) => void;
    setViewingMuscleGroupId: (v: string | null) => void;
    setViewingBodyPartId: (v: string | null) => void;
    setViewingChainId: (v: string | null) => void;
    setViewingMuscleCategoryName: (v: string | null) => void;
    setExerciseToAddId: (v: string | null | ((p: string | null) => string | null)) => void;
    setEditingCustomExerciseData: (v: CustomExerciseModalData | null) => void;
    setEditingFoodData: (v: UIStoreState['editingFoodData']) => void;
    setPendingWorkoutForReadinessCheck: (v: UIStoreState['pendingWorkoutForReadinessCheck'] | ((p: UIStoreState['pendingWorkoutForReadinessCheck']) => UIStoreState['pendingWorkoutForReadinessCheck'])) => void;
    setEditingWorkoutSessionInfo: (v: UIStoreState['editingWorkoutSessionInfo']) => void;
    setEditingCategoryInfo: (v: UIStoreState['editingCategoryInfo']) => void;
    setFoodItemToAdd_to_pantry: (v: FoodItem | null) => void;
    setPendingNavigation: (v: any) => void;
    setSaveSessionTrigger: (v: number | ((p: number) => number)) => void;
    setAddExerciseTrigger: (v: number | ((p: number) => number)) => void;
    setSaveProgramTrigger: (v: number | ((p: number) => number)) => void;
    setSaveLoggedWorkoutTrigger: (v: number | ((p: number) => number)) => void;
    setModifyWorkoutTrigger: (v: number | ((p: number) => number)) => void;
    setSearchQuery: (v: string | ((p: string) => string)) => void;
    setActiveSubTabs: (v: Record<string, string> | ((p: Record<string, string>) => Record<string, string>)) => void;
    setCurrentBackgroundOverride: (v: SessionBackground | undefined | ((p: SessionBackground | undefined) => SessionBackground | undefined)) => void;
    setRestTimer: (v: UIStoreState['restTimer'] | ((p: UIStoreState['restTimer']) => UIStoreState['restTimer'])) => void;
    setIsDirty: (v: boolean | ((p: boolean) => boolean)) => void;
    setKpknaction: (v: any) => void;
    setPendingCoachBriefing: (v: string | null | ((p: string | null) => string | null)) => void;
    setPendingWorkoutAfterBriefing: (v: UIStoreState['pendingWorkoutAfterBriefing'] | ((p: UIStoreState['pendingWorkoutAfterBriefing']) => UIStoreState['pendingWorkoutAfterBriefing'])) => void;
    setIsOnline: (v: boolean) => void;
    setInstallPromptEvent: (v: any) => void;
    setToasts: (v: ToastData[] | ((p: ToastData[]) => ToastData[])) => void;
    setOnExerciseCreated: (v: ((exercise: ExerciseMuscleInfo) => void) | null | ((p: ((exercise: ExerciseMuscleInfo) => void) | null) => ((exercise: ExerciseMuscleInfo) => void) | null)) => void;
    addToast: (message: string, type?: ToastData['type'], title?: string, duration?: number, why?: string) => void;
    removeToast: (id: number) => void;
}

function applyUpdater<T>(current: T, updater: T | ((prev: T) => T)): T {
    return typeof updater === 'function'
        ? (updater as (prev: T) => T)(current)
        : updater;
}

export const useUIStore = create<UIStoreState>()(
    immer((set) => ({
        view: 'home' as View,
        historyStack: [{ view: 'home' as View }],

        isBodyLogModalOpen: false,
        isNutritionLogModalOpen: false,
        isMeasurementsModalOpen: false,
        isStartWorkoutModalOpen: false,
        isCustomExerciseEditorOpen: false,
        isFoodEditorOpen: false,
        isFinishModalOpen: false,
        isTimeSaverModalOpen: false,
        isTimersModalOpen: false,
        isReadinessModalOpen: false,
        isAddToPlaylistSheetOpen: false,
        isWorkoutEditorOpen: false,
        isMuscleListEditorOpen: false,
        isLiveCoachActive: false,
        isLogActionSheetOpen: false,
        isWorkoutExitModalOpen: false,
        isAddPantryItemModalOpen: false,
        isSpecialSessionModalOpen: false,
        specialSessionData: null,
        isMenuOpen: false,
        isGlobalVoiceActive: false,

        activeProgramId: null,
        editingProgramId: null,
        editingSessionInfo: null,
        loggingSessionInfo: null,
        viewingSessionInfo: null,
        activeSession: null,
        viewingExerciseId: null,
        viewingFoodId: null,
        viewingMuscleGroupId: null,
        viewingJointId: null,
        viewingTendonId: null,
        viewingMovementPatternId: null,
        viewingBodyPartId: null,
        viewingChainId: null,
        viewingMuscleCategoryName: null,
        exerciseToAddId: null,
        editingCustomExerciseData: null,
        editingFoodData: null,
        pendingWorkoutForReadinessCheck: null,
        editingWorkoutSessionInfo: null,
        editingCategoryInfo: null,
        foodItemToAdd_to_pantry: null,
        pendingNavigation: null,

        saveSessionTrigger: 0,
        addExerciseTrigger: 0,
        saveProgramTrigger: 0,
        saveLoggedWorkoutTrigger: 0,
        modifyWorkoutTrigger: 0,

        searchQuery: '',
        activeSubTabs: {},

        currentBackgroundOverride: undefined,
        restTimer: null,
        isDirty: false,
        kpknAction: null,
        pendingCoachBriefing: null,
        pendingWorkoutAfterBriefing: null,

        isOnline: typeof navigator !== 'undefined' ? navigator.onLine : true,
        installPromptEvent: null,
        toasts: [],
        onExerciseCreated: null,

        // --- Setters ---
        setView: (v) => set((s) => { s.view = v; }),
        setHistoryStack: (u) => set((s) => { s.historyStack = applyUpdater(s.historyStack, u); }),
        setIsBodyLogModalOpen: (v) => set((s) => { s.isBodyLogModalOpen = applyUpdater(s.isBodyLogModalOpen, v); }),
        setIsNutritionLogModalOpen: (v) => set((s) => { s.isNutritionLogModalOpen = applyUpdater(s.isNutritionLogModalOpen, v); }),
        setIsMeasurementsModalOpen: (v) => set((s) => { s.isMeasurementsModalOpen = applyUpdater(s.isMeasurementsModalOpen, v); }),
        setIsStartWorkoutModalOpen: (v) => set((s) => { s.isStartWorkoutModalOpen = applyUpdater(s.isStartWorkoutModalOpen, v); }),
        setIsCustomExerciseEditorOpen: (v) => set((s) => { s.isCustomExerciseEditorOpen = applyUpdater(s.isCustomExerciseEditorOpen, v); }),
        setIsFoodEditorOpen: (v) => set((s) => { s.isFoodEditorOpen = applyUpdater(s.isFoodEditorOpen, v); }),
        setIsFinishModalOpen: (v) => set((s) => { s.isFinishModalOpen = applyUpdater(s.isFinishModalOpen, v); }),
        setIsTimeSaverModalOpen: (v) => set((s) => { s.isTimeSaverModalOpen = applyUpdater(s.isTimeSaverModalOpen, v); }),
        setIsTimersModalOpen: (v) => set((s) => { s.isTimersModalOpen = applyUpdater(s.isTimersModalOpen, v); }),
        setIsReadinessModalOpen: (v) => set((s) => { s.isReadinessModalOpen = applyUpdater(s.isReadinessModalOpen, v); }),
        setIsAddToPlaylistSheetOpen: (v) => set((s) => { s.isAddToPlaylistSheetOpen = applyUpdater(s.isAddToPlaylistSheetOpen, v); }),
        setIsWorkoutEditorOpen: (v) => set((s) => { s.isWorkoutEditorOpen = applyUpdater(s.isWorkoutEditorOpen, v); }),
        setIsMuscleListEditorOpen: (v) => set((s) => { s.isMuscleListEditorOpen = applyUpdater(s.isMuscleListEditorOpen, v); }),
        setIsLiveCoachActive: (v) => set((s) => { s.isLiveCoachActive = applyUpdater(s.isLiveCoachActive, v); }),
        setIsLogActionSheetOpen: (v) => set((s) => { s.isLogActionSheetOpen = applyUpdater(s.isLogActionSheetOpen, v); }),
        setIsWorkoutExitModalOpen: (v) => set((s) => { s.isWorkoutExitModalOpen = applyUpdater(s.isWorkoutExitModalOpen, v); }),
        setIsAddPantryItemModalOpen: (v) => set((s) => { s.isAddPantryItemModalOpen = applyUpdater(s.isAddPantryItemModalOpen, v); }),
        setIsSpecialSessionModalOpen: (v) => set((s) => { s.isSpecialSessionModalOpen = applyUpdater(s.isSpecialSessionModalOpen, v); }),
        setSpecialSessionData: (v) => set((s) => { s.specialSessionData = v; }),
        setIsMenuOpen: (v) => set((s) => { s.isMenuOpen = applyUpdater(s.isMenuOpen, v); }),
        setIsGlobalVoiceActive: (v) => set((s) => { s.isGlobalVoiceActive = applyUpdater(s.isGlobalVoiceActive, v); }),
        setActiveProgramId: (v) => set((s) => { s.activeProgramId = v; }),
        setEditingProgramId: (v) => set((s) => { s.editingProgramId = v; }),
        setEditingSessionInfo: (v) => set((s) => { s.editingSessionInfo = v; }),
        setLoggingSessionInfo: (v) => set((s) => { s.loggingSessionInfo = v; }),
        setViewingSessionInfo: (v) => set((s) => { s.viewingSessionInfo = v; }),
        setActiveSession: (v) => set((s) => { s.activeSession = v; }),
        setViewingExerciseId: (v) => set((s) => { s.viewingExerciseId = v; }),
        setViewingFoodId: (v) => set((s) => { s.viewingFoodId = v; }),
        setViewingMuscleGroupId: (v) => set((s) => { s.viewingMuscleGroupId = v; }),
        setViewingJointId: (v) => set((s) => { s.viewingJointId = v; }),
        setViewingTendonId: (v) => set((s) => { s.viewingTendonId = v; }),
        setViewingMovementPatternId: (v) => set((s) => { s.viewingMovementPatternId = v; }),
        setViewingBodyPartId: (v) => set((s) => { s.viewingBodyPartId = v; }),
        setViewingChainId: (v) => set((s) => { s.viewingChainId = v; }),
        setViewingMuscleCategoryName: (v) => set((s) => { s.viewingMuscleCategoryName = v; }),
        setExerciseToAddId: (v) => set((s) => { s.exerciseToAddId = applyUpdater(s.exerciseToAddId, v); }),
        setEditingCustomExerciseData: (v) => set((s) => { s.editingCustomExerciseData = v; }),
        setEditingFoodData: (v) => set((s) => { s.editingFoodData = v; }),
        setPendingWorkoutForReadinessCheck: (v) => set((s) => { s.pendingWorkoutForReadinessCheck = applyUpdater(s.pendingWorkoutForReadinessCheck, v); }),
        setEditingWorkoutSessionInfo: (v) => set((s) => { s.editingWorkoutSessionInfo = v; }),
        setEditingCategoryInfo: (v) => set((s) => { s.editingCategoryInfo = v; }),
        setFoodItemToAdd_to_pantry: (v) => set((s) => { s.foodItemToAdd_to_pantry = v; }),
        setPendingNavigation: (v) => set((s) => { s.pendingNavigation = v; }),
        setSaveSessionTrigger: (v) => set((s) => { s.saveSessionTrigger = applyUpdater(s.saveSessionTrigger, v); }),
        setAddExerciseTrigger: (v) => set((s) => { s.addExerciseTrigger = applyUpdater(s.addExerciseTrigger, v); }),
        setSaveProgramTrigger: (v) => set((s) => { s.saveProgramTrigger = applyUpdater(s.saveProgramTrigger, v); }),
        setSaveLoggedWorkoutTrigger: (v) => set((s) => { s.saveLoggedWorkoutTrigger = applyUpdater(s.saveLoggedWorkoutTrigger, v); }),
        setModifyWorkoutTrigger: (v) => set((s) => { s.modifyWorkoutTrigger = applyUpdater(s.modifyWorkoutTrigger, v); }),
        setSearchQuery: (v) => set((s) => { s.searchQuery = applyUpdater(s.searchQuery, v); }),
        setActiveSubTabs: (v) => set((s) => { s.activeSubTabs = applyUpdater(s.activeSubTabs, v); }),
        setCurrentBackgroundOverride: (v) => set((s) => { s.currentBackgroundOverride = applyUpdater(s.currentBackgroundOverride, v); }),
        setRestTimer: (v) => set((s) => { s.restTimer = applyUpdater(s.restTimer, v); }),
        setIsDirty: (v) => set((s) => { s.isDirty = applyUpdater(s.isDirty, v); }),
        setKpknaction: (v) => set((s) => { s.kpknAction = v; }),
        setPendingCoachBriefing: (v) => set((s) => { s.pendingCoachBriefing = applyUpdater(s.pendingCoachBriefing, v); }),
        setPendingWorkoutAfterBriefing: (v) => set((s) => { s.pendingWorkoutAfterBriefing = applyUpdater(s.pendingWorkoutAfterBriefing, v); }),
        setIsOnline: (v) => set((s) => { s.isOnline = v; }),
        setInstallPromptEvent: (v) => set((s) => { s.installPromptEvent = v; }),
        setToasts: (v) => set((s) => { s.toasts = applyUpdater(s.toasts, v); }),
        setOnExerciseCreated: (v) => set((s) => { s.onExerciseCreated = applyUpdater(s.onExerciseCreated, v); }),

        addToast: (message, type = 'success', title, duration, why) => set((s) => {
            s.toasts.push({ id: Date.now(), message, type, title, duration, why });
        }),
        removeToast: (id) => set((s) => {
            s.toasts = s.toasts.filter(t => t.id !== id);
        }),
    }))
);
