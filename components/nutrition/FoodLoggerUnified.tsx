import React from 'react';
import { useAppState } from '../../contexts/AppContext';
import type { NutritionLog } from '../../types';
import { RegisterFoodDrawer } from './RegisterFoodDrawer';

interface FoodLoggerUnifiedProps {
    initialDate?: string;
    mealType?: NutritionLog['mealType'];
    onLogRegistered?: (log: NutritionLog) => void;
}

const FoodLoggerUnified: React.FC<FoodLoggerUnifiedProps> = ({
    initialDate,
    mealType = 'lunch',
    onLogRegistered,
}) => {
    const { settings } = useAppState();

    return (
        <section className="space-y-3">
            <div className="rounded-[28px] border border-black/[0.05] bg-white/80 px-4 py-4">
                <p className="text-[10px] font-black uppercase tracking-[0.18em] text-[#49454F]">Registro rápido</p>
                <h2 className="mt-1 text-lg font-black text-[#1D1B20]">Describe tu comida y guarda una referencia útil</h2>
                <p className="mt-1.5 text-sm text-[#49454F]">
                    Escribe con libertad, analiza al final y guarda un resultado práctico. Si algo no queda perfecto,
                    siempre podrás ajustarlo después.
                </p>
            </div>

            <RegisterFoodDrawer
                isOpen
                onClose={() => {}}
                onSave={onLogRegistered ?? (() => {})}
                settings={settings}
                initialDate={initialDate}
                mealType={mealType}
                displayMode="appendix"
                showCloseButton={false}
            />
        </section>
    );
};

export default FoodLoggerUnified;
