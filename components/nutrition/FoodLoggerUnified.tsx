import React, { useEffect, useMemo, useState } from 'react';
import type { NutritionConnectivitySnapshot } from '../../services/nutritionConnectivityService';
import { getNutritionConnectivity } from '../../services/nutritionConnectivityService';
import { useAppState } from '../../contexts/AppContext';
import type { NutritionLog } from '../../types';
import { AlertTriangleIcon, BrainIcon, SparklesIcon } from '../icons';
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
    const [connectivity, setConnectivity] = useState<NutritionConnectivitySnapshot | null>(null);

    useEffect(() => {
        let cancelled = false;

        const loadConnectivity = async () => {
            try {
                const snapshot = await getNutritionConnectivity(settings, true);
                if (!cancelled) {
                    setConnectivity(snapshot);
                }
            } catch {
                if (!cancelled) {
                    setConnectivity(null);
                }
            }
        };

        loadConnectivity();
        return () => {
            cancelled = true;
        };
    }, [settings]);

    const statusPills = useMemo(() => {
        const pills: Array<{ label: string; tone: 'neutral' | 'success' | 'warning' }> = [
            {
                label: settings.nutritionDescriptionMode === 'rules' ? 'Modo reglas' : 'Parser híbrido',
                tone: 'neutral',
            },
        ];

        if (connectivity?.localAiAvailable) {
            pills.push({
                label: connectivity.localAiModel ? `IA local: ${connectivity.localAiModel}` : 'IA local disponible',
                tone: 'success',
            });
        } else {
            pills.push({
                label: 'IA local no disponible',
                tone: 'warning',
            });
        }

        if (connectivity?.canUseInternetApis) {
            pills.push({ label: 'Catálogos online activos', tone: 'neutral' });
        } else {
            pills.push({ label: 'Modo offline priorizado', tone: 'neutral' });
        }

        return pills;
    }, [connectivity, settings.nutritionDescriptionMode]);

    const getPillClass = (tone: 'neutral' | 'success' | 'warning') => {
        if (tone === 'success') {
            return 'bg-emerald-50 text-emerald-700 border-emerald-200';
        }
        if (tone === 'warning') {
            return 'bg-amber-50 text-amber-700 border-amber-200';
        }
        return 'bg-white/70 text-[#49454F] border-black/[0.06]';
    };

    return (
        <section className="space-y-3">
            <div className="rounded-[28px] border border-black/[0.05] bg-white/70 backdrop-blur px-4 py-4">
                <div className="flex items-start justify-between gap-3">
                    <div>
                        <p className="text-[10px] font-black uppercase tracking-[0.18em] text-[#49454F]">Registro descriptivo</p>
                        <h2 className="text-lg font-black text-[#1D1B20] mt-1">Describe la comida y corrige si hace falta</h2>
                        <p className="text-sm text-[#49454F] mt-1.5">
                            El flujo usa parser estructurado, búsqueda real en la base de alimentos y IA local solo para refinar etiquetas cuando está disponible.
                        </p>
                    </div>
                    <div className="w-11 h-11 rounded-2xl bg-[#1D1B20] text-white flex items-center justify-center shrink-0">
                        {connectivity?.localAiAvailable ? <SparklesIcon size={18} /> : <BrainIcon size={18} />}
                    </div>
                </div>

                <div className="flex flex-wrap gap-2 mt-4">
                    {statusPills.map((pill) => (
                        <span
                            key={pill.label}
                            className={`px-3 py-1.5 rounded-full border text-[10px] font-black uppercase tracking-[0.14em] ${getPillClass(pill.tone)}`}
                        >
                            {pill.label}
                        </span>
                    ))}
                </div>

                {!connectivity?.localAiAvailable && (
                    <div className="mt-3 flex items-start gap-2 rounded-2xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-amber-700">
                        <AlertTriangleIcon size={14} className="mt-0.5 shrink-0" />
                        <p className="text-[11px] leading-relaxed">
                            Si levantas el backend local con Ollama, este mismo flujo puede usar IA local para mejorar la interpretación sin cambiar la UI.
                        </p>
                    </div>
                )}
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
