// components/NutritionView.tsx
// Vista principal de Nutrición: Dashboard cockpit + Drawer de registro

import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { NutritionDashboard, RegisterFoodDrawer, NutritionWizard, NutritionPlanLanding } from './nutrition/index';

const NutritionView: React.FC = () => {
    const { settings } = useAppState();
    const { handleSaveNutritionLog } = useAppDispatch();
    const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [showWizard, setShowWizard] = useState(false);

    if (!settings.hasSeenNutritionWizard) {
        if (!showWizard) {
            return <NutritionPlanLanding onStartWizard={() => setShowWizard(true)} />;
        }
        return <NutritionWizard onComplete={() => setShowWizard(false)} />;
    }

    return (
        <div className="max-w-md mx-auto px-4 pt-4">
            <NutritionDashboard
                selectedDate={selectedDate}
                onDateChange={setSelectedDate}
                onOpenDrawer={() => setIsDrawerOpen(true)}
            />
            <RegisterFoodDrawer
                isOpen={isDrawerOpen}
                onClose={() => setIsDrawerOpen(false)}
                onSave={handleSaveNutritionLog}
                settings={settings}
                initialDate={selectedDate}
            />
        </div>
    );
};

export default NutritionView;
