// components/NutritionView.tsx
// Vista principal de Nutrición: estructura tipo ProgramDetail (hero + tabs + cards)

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateDailyCalorieGoal } from '../utils/calorieFormulas';
import { NutritionDashboard, RegisterFoodDrawer, NutritionWizard, NutritionSetupModal, useNutritionStats, CalorieGoalCard, NutritionPlanEditorModal } from './nutrition/index';
import NutritionHeroBanner from './nutrition/NutritionHeroBanner';
import { getLocalDateString } from '../utils/dateUtils';

const NutritionView: React.FC = () => {
    const { settings } = useAppState();
    const { handleSaveNutritionLog, setSettings } = useAppDispatch();
    const [selectedDate, setSelectedDate] = useState(getLocalDateString());
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [showWizard, setShowWizard] = useState(false);
    const [activeTab, setActiveTab] = useState<'hoy' | 'plan'>('hoy');

    const { dailyCalories, calorieGoal, hasCalorieGoal } = useNutritionStats(selectedDate);
    const needsSetupModal = !settings.hasSeenNutritionWizard && !settings.hasDismissedNutritionSetup;
    const hasDismissed = settings.hasDismissedNutritionSetup && !settings.hasSeenNutritionWizard;

    if (showWizard) {
        return (
            <NutritionWizard
                onComplete={() => setShowWizard(false)}
            />
        );
    }

    return (
        <div className="flex flex-col min-h-full bg-black">
            <NutritionHeroBanner
                selectedDate={selectedDate}
                onDateChange={setSelectedDate}
                dailyCalories={dailyCalories}
                calorieGoal={calorieGoal}
                hasCalorieGoal={hasCalorieGoal}
            />

            <div className="flex border-b border-white/5 shrink-0">
                <button
                    onClick={() => setActiveTab('hoy')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'hoy' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Hoy
                </button>
                <button
                    onClick={() => setActiveTab('plan')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'plan' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Plan
                </button>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto">
                {activeTab === 'hoy' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area">
                        <NutritionDashboard
                            selectedDate={selectedDate}
                            onDateChange={setSelectedDate}
                            onOpenDrawer={() => setIsDrawerOpen(true)}
                            showSetupBanner={hasDismissed}
                            onOpenWizard={() => setShowWizard(true)}
                            hideHeader
                        />
                    </div>
                )}
                {activeTab === 'plan' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area">
                        <NutritionPlanTab />
                    </div>
                )}
            </div>

            <RegisterFoodDrawer
                isOpen={isDrawerOpen}
                onClose={() => setIsDrawerOpen(false)}
                onSave={handleSaveNutritionLog}
                settings={settings}
                initialDate={selectedDate}
            />
            <NutritionSetupModal
                isOpen={needsSetupModal}
                onConfigurarAhora={() => setShowWizard(true)}
                onConfigurarDespues={() => setSettings({ hasDismissedNutritionSetup: true })}
            />
        </div>
    );
};

const NutritionPlanTab: React.FC = () => {
    const { settings } = useAppState();
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );
    return (
        <div className="space-y-4">
            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Objetivo calórico</h3>
                <CalorieGoalCard calorieGoal={calorieGoal} onEditClick={() => setIsGoalModalOpen(true)} />
            </div>
            <NutritionPlanEditorModal isOpen={isGoalModalOpen} onClose={() => setIsGoalModalOpen(false)} />
        </div>
    );
};

export default NutritionView;
