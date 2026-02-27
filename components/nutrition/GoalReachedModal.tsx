// components/nutrition/GoalReachedModal.tsx
// Modal cuando estimatedEndDate ha pasado: ¿Alcanzaste tu meta?

import React from 'react';
import { TacticalModal } from '../ui/TacticalOverlays';
import Button from '../ui/Button';
import { NutritionPlan } from '../../types';

const TOLERANCE_WEIGHT_KG = 2;
const TOLERANCE_PCT = 2;

interface GoalReachedModalProps {
    isOpen: boolean;
    onClose: () => void;
    plan: NutritionPlan | null;
    currentWeight?: number;
    currentBodyFat?: number;
    currentMuscle?: number;
    onUpdateData: () => void;
    onCelebrate: () => void;
    onAdjustPlan: () => void;
}

export const GoalReachedModal: React.FC<GoalReachedModalProps> = ({
    isOpen,
    onClose,
    plan,
    currentWeight,
    currentBodyFat,
    currentMuscle,
    onUpdateData,
    onCelebrate,
    onAdjustPlan,
}) => {
    if (!plan || !isOpen) return null;

    const isWithinTolerance = (): boolean | null => {
        if (plan.goalType === 'weight') {
            if (currentWeight == null) return null;
            return Math.abs(currentWeight - plan.goalValue) <= TOLERANCE_WEIGHT_KG;
        }
        if (plan.goalType === 'bodyFat') {
            if (currentBodyFat == null) return null;
            return Math.abs(currentBodyFat - plan.goalValue) <= TOLERANCE_PCT;
        }
        if (plan.goalType === 'muscleMass') {
            if (currentMuscle == null) return null;
            return Math.abs(currentMuscle - plan.goalValue) <= TOLERANCE_PCT;
        }
        return null;
    };

    const hasData =
        (plan.goalType === 'weight' && currentWeight != null) ||
        (plan.goalType === 'bodyFat' && currentBodyFat != null) ||
        (plan.goalType === 'muscleMass' && currentMuscle != null);

    const within = isWithinTolerance();

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="¿Alcanzaste tu meta?">
            <div className="space-y-4 p-1">
                <p className="text-slate-300 text-sm">
                    La fecha estimada para tu objetivo ha pasado. ¿Lograste alcanzar tu meta?
                </p>
                {!hasData && (
                    <p className="text-amber-400/90 text-xs">
                        Actualiza tus datos corporales para verificar si alcanzaste la meta (tolerancia ±{TOLERANCE_WEIGHT_KG} kg / ±{TOLERANCE_PCT}%).
                    </p>
                )}
                {hasData && within === true && (
                    <p className="text-green-400 text-sm font-bold">¡Felicidades! Estás dentro de la tolerancia de tu meta.</p>
                )}
                {hasData && within === false && (
                    <p className="text-slate-400 text-sm">Aún no estás dentro de la tolerancia. Puedes ajustar el plan o continuar.</p>
                )}
                <div className="flex flex-col gap-2 pt-2">
                    <Button onClick={onUpdateData} variant="secondary" className="w-full">
                        Actualizar datos
                    </Button>
                    {hasData && within === true && (
                        <Button onClick={onCelebrate} className="w-full">
                            ¡Celebrar y nuevo objetivo!
                        </Button>
                    )}
                    {hasData && within === false && (
                        <Button onClick={onAdjustPlan} variant="secondary" className="w-full">
                            Ajustar plan
                        </Button>
                    )}
                    <Button onClick={onClose} variant="secondary" className="w-full">
                        Cerrar
                    </Button>
                </div>
            </div>
        </TacticalModal>
    );
};
