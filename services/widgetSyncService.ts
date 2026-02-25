// services/widgetSyncService.ts
// Sincroniza datos con los widgets nativos de Android (SharedPreferences).
// Solo se ejecuta en plataforma Android nativa.

import { Capacitor } from '@capacitor/core';
import { WidgetBridge } from 'capacitor-widget-bridge';
import { calculateGlobalBatteriesAsync } from './computeWorkerService';
import { calculateDailyCalorieGoal } from '../utils/calorieFormulas';
import { getLocalDateString } from '../utils/dateUtils';
import { calculateWeeklyEffectiveVolumeByMuscleGroup } from './analysisService';
import type {
    Program,
    Session,
    WorkoutLog,
    Settings,
    NutritionLog,
    SleepLog,
    DailyWellbeingLog,
    ActiveProgramState,
    ExerciseMuscleInfo,
} from '../types';

export interface WidgetSyncState {
    programs: Program[];
    activeProgramState: ActiveProgramState | null;
    history: WorkoutLog[];
    sleepLogs: SleepLog[];
    dailyWellbeingLogs: DailyWellbeingLog[];
    nutritionLogs: NutritionLog[];
    settings: Settings;
    exerciseList: ExerciseMuscleInfo[];
}

const DEBOUNCE_MS = 10_000; // 10 segundos entre syncs (para ver volumen en tiempo real)
let lastSyncTime = 0;

function getTodaysSession(state: WidgetSyncState): { session: Session; program: Program } | null {
    const { programs, activeProgramState } = state;
    if (!activeProgramState || activeProgramState.programId === undefined) return null;
    const program = programs.find(p => p.id === activeProgramState.programId);
    if (!program) return null;
    const todayCalendarDay = new Date().getDay();
    const currentWeekId = activeProgramState.currentWeekId;

    for (const macro of program.macrocycles || []) {
        for (const block of macro.blocks || []) {
            for (const meso of block.mesocycles || []) {
                const week = meso.weeks?.find(w => w.id === currentWeekId);
                if (!week) continue;
                const session = week.sessions?.find(
                    s => s.dayOfWeek !== undefined && s.dayOfWeek === todayCalendarDay
                );
                if (session) return { session, program };
            }
        }
    }
    return null;
}

/**
 * Sincroniza próxima sesión y batería AUGE con los widgets nativos de Android.
 * Solo se ejecuta en Android; en web/PWA es no-op.
 * Usa debounce para no saturar (máx. 1 sync cada 45s).
 */
export async function syncWidgetData(state: WidgetSyncState): Promise<void> {
    if (!Capacitor.isNativePlatform() || Capacitor.getPlatform() !== 'android') return;
    const now = Date.now();
    if (now - lastSyncTime < DEBOUNCE_MS) return;

    try {
        const todays = getTodaysSession(state);
        const nextSessionJson = JSON.stringify(
            todays
                ? { sessionName: todays.session.name, programName: todays.program.name }
                : { sessionName: 'Ninguna sesión hoy', programName: 'Tu Programa' }
        );
        await WidgetBridge.setItem({ key: 'next_session', value: nextSessionJson });

        const batteries = await calculateGlobalBatteriesAsync(
            state.history,
            state.sleepLogs,
            state.dailyWellbeingLogs,
            state.nutritionLogs,
            state.settings,
            state.exerciseList
        );
        const batteryJson = JSON.stringify({
            cns: batteries.cns,
            muscular: batteries.muscular,
            spinal: batteries.spinal,
        });
        await WidgetBridge.setItem({ key: 'battery_auge', value: batteryJson });

        const todayStr = getLocalDateString();
        const nutritionLogs = state.nutritionLogs.filter(
            l => l.date?.startsWith(todayStr) && (l.status === 'consumed' || !l.status)
        );
        const acc = { calories: 0, protein: 0, carbs: 0, fats: 0 };
        nutritionLogs.forEach(log => {
            (log.foods || []).forEach((f: { calories?: number; protein?: number; carbs?: number; fats?: number }) => {
                acc.calories += f.calories || 0;
                acc.protein += f.protein || 0;
                acc.carbs += f.carbs || 0;
                acc.fats += f.fats || 0;
            });
        });
        const calorieGoal = calculateDailyCalorieGoal(state.settings, state.settings.calorieGoalConfig);
        const nutritionJson = JSON.stringify({
            calories: Math.round(acc.calories),
            protein: Math.round(acc.protein),
            carbs: Math.round(acc.carbs),
            fats: Math.round(acc.fats),
            calorieGoal,
        });
        await WidgetBridge.setItem({ key: 'nutrition', value: nutritionJson });

        // Volumen efectivo semanal: realizado vs programado
        const muscleData = calculateWeeklyEffectiveVolumeByMuscleGroup(
            state.programs,
            state.history,
            state.settings,
            state.exerciseList,
            state.settings.muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} }
        );
        const completed = muscleData.reduce((s, m) => s + m.completed, 0);
        const planned = muscleData.reduce((s, m) => s + m.planned, 0);
        const effectiveVolumeJson = JSON.stringify({ completed, planned });
        await WidgetBridge.setItem({ key: 'effective_volume', value: effectiveVolumeJson });

        await WidgetBridge.reloadWidget();
        lastSyncTime = now;
    } catch (e) {
        console.warn('[widgetSync] Error syncing widget data:', e);
    }
}
