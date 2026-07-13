import React, { useEffect, useState, useMemo } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { getCachedAdaptiveData } from '../services/augeAdaptiveService';
import { GPFatigueCurve, BayesianConfidence } from './ui/AugeDeepView';

export const PCEActionModal: React.FC = () => {
    const [pceData, setPceData] = useState<any>(null);
    const { settings, setSettings, addToast, navigateTo } = useAppContext();

    useEffect(() => {
        const handleTrigger = (e: any) => setPceData(e.detail);
        window.addEventListener('auge-pce-triggered', handleTrigger);
        return () => window.removeEventListener('auge-pce-triggered', handleTrigger);
    }, []);

    const adaptiveCache = useMemo(() => getCachedAdaptiveData(), []);

    if (!pceData) return null;

    const handleAcceptDiet = () => {
        if (pceData.suggestedCalories) {
            setSettings({ ...settings, dailyCalorieGoal: pceData.suggestedCalories });
            addToast(`Meta ajustada a ${pceData.suggestedCalories} kcal por hoy para máxima reparación.`, 'success');
        }
    };

    const handleTrainingAction = () => {
        if (pceData.isExtreme) {
            addToast('Sesión de mañana cancelada. Día de descanso obligatorio establecido.', 'success');
        } else {
            try {
                sessionStorage.setItem('pce-pending-modification', JSON.stringify({ score: pceData.score, message: 'AUGE sugirió reducir el volumen de tu próxima sesión.' }));
            } catch (_) {}
            addToast('Al abrir el Session Editor verás la sugerencia de AUGE.', 'suggestion');
            navigateTo('home');
        }
        setPceData(null);
    };

    return (
        <div className="fixed inset-0 bg-black/90 z-[9999] flex items-center justify-center p-4 backdrop-blur-sm animate-fade-in">
            <div className="bg-gray-900 rounded-2xl p-6 max-w-md w-full border border-red-500/50 shadow-2xl shadow-red-900/20">
                <div className="flex items-center gap-3 mb-4">
                    <span className="text-3xl">⚠️</span>
                    <div>
                        <h2 className="text-xl font-bold text-red-500 leading-tight">Sobrecarga Crítica (SNC)</h2>
                        <span className="text-xs text-red-400 font-mono">AUGE Score: {pceData.score}</span>
                    </div>
                </div>
                
                <p className="text-gray-300 text-sm mb-6">
                    Has generado un daño tisular y neural masivo en esta sesión. Si no aplicas medidas inmediatas, tu progreso se estancará. Selecciona tus contramedidas:
                </p>

                <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2 custom-scrollbar">
                    
                    {/* Tarjeta de Nutrición Condicional */}
                    {pceData.suggestedCalories && (
                        <div className="bg-gray-800/80 border border-gray-700 p-4 rounded-xl">
                            <h3 className="font-bold text-blue-400 mb-1 flex items-center gap-2">🍗 Nutrición Táctica</h3>
                            <p className="text-sm text-gray-400 mb-3">Sube tu meta calórica a <strong className="text-white">{pceData.suggestedCalories} kcal</strong> (+350) para frenar el catabolismo severo hoy.</p>
                            <button onClick={handleAcceptDiet} className="w-full bg-blue-600/20 text-blue-400 border border-blue-600/50 py-2 rounded-lg font-semibold hover:bg-blue-600 hover:text-white transition-colors">
                                Aplicar Superávit Hoy
                            </button>
                        </div>
                    )}

                    {/* Tarjeta de Sueño Biológico */}
                    <div className="bg-gray-800/80 border border-gray-700 p-4 rounded-xl">
                        <h3 className="font-bold text-indigo-400 mb-1 flex items-center gap-2">💤 Sleep Banking</h3>
                        <p className="text-sm text-gray-400 mb-3">Sueles despertar a las {pceData.wakeTimeStr}. Para lograr 9h de supercompensación y picos de GH, debes estar en la cama a las <strong className="text-white text-base">{pceData.suggestedSleepTime}</strong>.</p>
                        <button onClick={() => addToast('¡Alarma mental configurada!', 'success')} className="w-full bg-indigo-600/20 text-indigo-400 border border-indigo-600/50 py-2 rounded-lg font-semibold hover:bg-indigo-600 hover:text-white transition-colors">
                            Entendido
                        </button>
                    </div>

                    {/* Tarjeta de Entrenamiento */}
                    <div className="bg-gray-800/80 border border-gray-700 p-4 rounded-xl">
                        <h3 className="font-bold text-cyber-cyan mb-1 flex items-center gap-2">🏋️ Auto-Regulación</h3>
                        <p className="text-sm text-gray-400 mb-3">
                            {pceData.isExtreme 
                                ? "Tu sistema central superó el límite crítico. Sugerimos convertir tu próxima sesión en un Día de Descanso." 
                                : "Sugerimos reducir drásticamente el volumen (series) de tu sesión de mañana para asimilar el estímulo de hoy."}
                        </p>
                        <button onClick={handleTrainingAction} className="w-full bg-cyber-cyan/20 text-cyber-cyan border border-cyber-cyan/50 py-2 rounded-lg font-semibold hover:bg-cyber-cyan hover:text-white transition-colors">
                            {pceData.isExtreme ? "Cancelar Sesión de Mañana" : "Modificar Sesión de Mañana"}
                        </button>
                    </div>
                </div>

                {/* AUGE: Impacto Proyectado */}
                <div className="mt-4 bg-gray-800/60 border border-violet-500/20 p-4 rounded-xl">
                    <h3 className="font-bold text-violet-400 mb-3 flex items-center gap-2 text-sm">🧠 Impacto Proyectado AUGE</h3>
                    <div className="mb-3">
                        <p className="text-[10px] text-[#49454F] font-bold uppercase tracking-widest mb-2">Fatiga próximas 72h</p>
                        <GPFatigueCurve data={adaptiveCache.gpCurve} compact />
                    </div>
                    <div className="mb-3">
                        <p className="text-[10px] text-[#49454F] font-bold uppercase tracking-widest mb-2">Recuperación Bayesiana</p>
                        <BayesianConfidence totalObservations={adaptiveCache.totalObservations} personalizedRecoveryHours={adaptiveCache.personalizedRecoveryHours} compact />
                    </div>
                    {adaptiveCache.banister?.verdict && (
                        <div className="px-2 py-1.5 bg-black/40 rounded-lg border border-[#E6E0E9]">
                            <p className="text-[9px] text-zinc-300 font-medium italic">{adaptiveCache.banister.verdict}</p>
                        </div>
                    )}
                </div>

                <button 
                    onClick={() => setPceData(null)} 
                    className="mt-6 w-full text-gray-500 py-2 text-sm uppercase tracking-wider font-bold hover:text-gray-300 transition-colors"
                >
                    Ignorar advertencias
                </button>
            </div>
        </div>
    );
};