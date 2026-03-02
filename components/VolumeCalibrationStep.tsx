import React, { useState } from 'react';
import { AthleteProfileScore, Settings } from '../types';
import { InfoIcon, ChevronRightIcon, ArrowLeftIcon } from './icons';
import { getIsraetelVolumeRecommendations, getKpnkVolumeRecommendations } from '../services/volumeCalculator';
import AthleteProfilingWizard from './AthleteProfilingWizard';
import ToggleSwitch from './ui/ToggleSwitch';
import type { VolumeRecommendation as ProgramVolumeRec } from '../types';

export type VolumeSystem = 'israetel' | 'manual' | 'kpnk';

interface VolumeCalibrationStepProps {
    onComplete: (config: {
        volumeSystem: VolumeSystem;
        volumeRecommendations: ProgramVolumeRec[];
        volumeAlertsEnabled: boolean;
        athleteProfileScore?: AthleteProfileScore;
    }) => void;
    onBack: () => void;
    settings: Settings;
}

const VOLUME_INFO = `Son tus series por semana por músculo. Te ayudan a saber cuánto entrenar sin pasarte.`;

const ISRAETEL_INFO = `Rangos publicados para la mayoría. Muy buen punto de partida.`;

export const VolumeCalibrationStep: React.FC<VolumeCalibrationStepProps> = ({
    onComplete,
    onBack,
    settings,
}) => {
    const [view, setView] = useState<'choose' | 'manual' | 'kpnk-wizard' | 'summary'>('choose');
    const [volumeSystem, setVolumeSystem] = useState<VolumeSystem | null>(null);
    const [volumeRecommendations, setVolumeRecommendations] = useState<ProgramVolumeRec[]>([]);
    const [volumeAlertsEnabled, setVolumeAlertsEnabled] = useState(true);
    const [athleteProfileScore, setAthleteProfileScore] = useState<AthleteProfileScore | null>(null);
    const [showVolumeInfo, setShowVolumeInfo] = useState(false);
    const [showIsraetelInfo, setShowIsraetelInfo] = useState(false);

    const handleSelectIsraetel = () => {
        const recs = getIsraetelVolumeRecommendations();
        setVolumeRecommendations(recs);
        setVolumeSystem('israetel');
        setView('summary');
    };

    const handleSelectManual = () => {
        setVolumeSystem('manual');
        const base = getIsraetelVolumeRecommendations();
        setVolumeRecommendations(base.map(r => ({ ...r })));
        setView('manual');
    };

    const handleSelectKpnk = () => {
        setVolumeSystem('kpnk');
        setView('kpnk-wizard');
    };

    const handleKpnkComplete = (score: AthleteProfileScore) => {
        setAthleteProfileScore(score);
        const recs = getKpnkVolumeRecommendations(score, settings, 'Acumulación');
        setVolumeRecommendations(recs);
        setView('summary');
    };

    const handleManualConfirm = () => {
        setView('summary');
    };

    const handleSummaryConfirm = () => {
        if (volumeSystem && volumeRecommendations.length > 0) {
            onComplete({
                volumeSystem,
                volumeRecommendations,
                volumeAlertsEnabled,
                athleteProfileScore: athleteProfileScore || undefined,
            });
        }
    };

    const handleUpdateManualRec = (muscleGroup: string, field: keyof ProgramVolumeRec, value: number) => {
        setVolumeRecommendations(prev =>
            prev.map(r =>
                r.muscleGroup === muscleGroup ? { ...r, [field]: value } : r
            )
        );
    };

    // Vista: Resumen (después de Israetel, manual o KPKN)
    if (view === 'summary') {
        const isKpnk = volumeSystem === 'kpnk';
        return (
            <div className="fixed inset-0 z-[210] bg-[#1a1a1a] flex flex-col animate-fade-in-up safe-area-root">
                <div className="flex-1 overflow-y-auto p-6 custom-scrollbar">
                    <h2 className="text-lg font-medium text-white mb-1">
                        {isKpnk
                            ? 'Tu volumen recomendado por músculo'
                            : 'Tu volumen por músculo'}
                    </h2>
                    <p className="text-sm text-[#a3a3a3] mb-6">
                        Según tu perfil, estas son tus series por semana.
                    </p>

                    <div className="space-y-3 mb-8">
                        {volumeRecommendations.map(rec => {
                            const opt = Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2);
                            return (
                                <div
                                    key={rec.muscleGroup}
                                    className="bg-[#252525] rounded-xl p-4 border border-[#3f3f3f]"
                                >
                                    <div className="flex justify-between items-center mb-2">
                                        <span className="text-sm font-bold text-white">{rec.muscleGroup}</span>
                                        <div className="flex items-center gap-2">
                                            <span className="text-[10px] text-red-400/80">0-{rec.minEffectiveVolume - 1}</span>
                                            <span className="text-[10px] text-emerald-400 font-bold">{rec.minEffectiveVolume}-{rec.maxRecoverableVolume}</span>
                                            <span className="text-[10px] text-amber-400/80">{rec.maxRecoverableVolume + 1}+</span>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <input
                                            type="number"
                                            min={1}
                                            max={30}
                                            value={opt}
                                            onChange={e => {
                                                const target = parseInt(e.target.value, 10) || opt;
                                                const newMAV = Math.max(rec.minEffectiveVolume + 1, Math.min(rec.maxRecoverableVolume, 2 * target - rec.minEffectiveVolume));
                                                handleUpdateManualRec(rec.muscleGroup, 'maxAdaptiveVolume', newMAV);
                                            }}
                                            className="w-14 bg-black/50 border border-white/20 rounded px-2 py-1 text-xs text-white text-center"
                                        />
                                        <span className="text-[10px] text-zinc-500">series/semana objetivo</span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="flex items-center justify-between p-4 bg-[#252525] rounded-xl border border-[#3f3f3f] mb-6">
                        <div>
                            <p className="text-sm font-medium text-white">Alertas de volumen</p>
                            <p className="text-xs text-[#a3a3a3]">Te avisamos si te pasas al añadir ejercicios</p>
                        </div>
                        <ToggleSwitch checked={volumeAlertsEnabled} onChange={setVolumeAlertsEnabled} size="sm" />
                    </div>
                </div>

                <div className="p-6 border-t border-[#2a2a2a] wizard-safe-footer">
                    <button
                        onClick={handleSummaryConfirm}
                        className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm"
                    >
                        Continuar
                    </button>
                </div>
            </div>
        );
    }

    // Vista: Editor manual por músculo
    if (view === 'manual') {
        return (
            <div className="fixed inset-0 z-[210] bg-[#1a1a1a] flex flex-col animate-fade-in-up safe-area-root">
                <div className="p-4 border-b border-[#2a2a2a] flex items-center gap-3">
                    <button onClick={() => setView('choose')} className="p-2 -ml-2 text-[#737373] hover:text-white transition-colors">
                        <ArrowLeftIcon size={20} />
                    </button>
                    <h2 className="text-lg font-medium text-white">Tu volumen por músculo</h2>
                </div>
                <div className="flex-1 overflow-y-auto p-4 custom-scrollbar">
                    <p className="text-sm text-[#a3a3a3] mb-4">Indica tus series por semana para cada grupo.</p>
                    {volumeRecommendations.map(rec => (
                        <div key={rec.muscleGroup} className="mb-4 p-4 bg-[#252525] rounded-xl border border-[#3f3f3f]">
                            <p className="text-sm font-bold text-white mb-3">{rec.muscleGroup}</p>
                            <div className="grid grid-cols-3 gap-2">
                                <div>
                                    <label className="text-[9px] text-zinc-500 uppercase">Mínimo</label>
                                    <input
                                        type="number"
                                        min={0}
                                        max={30}
                                        value={rec.minEffectiveVolume}
                                        onChange={e => handleUpdateManualRec(rec.muscleGroup, 'minEffectiveVolume', parseInt(e.target.value, 10) || 0)}
                                        className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] text-zinc-500 uppercase">Objetivo</label>
                                    <input
                                        type="number"
                                        min={1}
                                        max={30}
                                        value={Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2)}
                                        onChange={e => {
                                            const v = parseInt(e.target.value, 10) || rec.maxAdaptiveVolume;
                                            handleUpdateManualRec(rec.muscleGroup, 'maxAdaptiveVolume', v);
                                        }}
                                        className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white"
                                    />
                                </div>
                                <div>
                                    <label className="text-[9px] text-zinc-500 uppercase">Máximo</label>
                                    <input
                                        type="number"
                                        min={1}
                                        max={40}
                                        value={rec.maxRecoverableVolume}
                                        onChange={e => handleUpdateManualRec(rec.muscleGroup, 'maxRecoverableVolume', parseInt(e.target.value, 10) || 20)}
                                        className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white"
                                    />
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
                <div className="p-6 border-t border-[#2a2a2a] wizard-safe-footer">
                    <button
                        onClick={handleManualConfirm}
                        className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm"
                    >
                        Continuar
                    </button>
                </div>
            </div>
        );
    }

    // Vista: KPKN Wizard (usa AthleteProfilingWizard)
    if (view === 'kpnk-wizard') {
        return (
            <AthleteProfilingWizard
                onComplete={handleKpnkComplete}
                onCancel={() => setView('choose')}
            />
        );
    }

    // Vista principal: selector único
    const handleSelectChange = (value: VolumeSystem) => {
        setVolumeSystem(value);
        if (value === 'israetel') {
            setVolumeRecommendations(getIsraetelVolumeRecommendations());
        } else if (value === 'manual') {
            setVolumeRecommendations(getIsraetelVolumeRecommendations().map(r => ({ ...r })));
        }
    };

    return (
        <div className="fixed inset-0 z-[210] bg-[#1a1a1a] flex flex-col animate-fade-in-up safe-area-root">
            <div className="p-6 border-b border-[#2a2a2a]">
                <div className="flex items-center gap-2 mb-2">
                    <button onClick={onBack} className="p-2 -ml-2 text-[#737373] hover:text-white transition-colors">
                        <ArrowLeftIcon size={20} />
                    </button>
                    <h2 className="text-lg font-medium text-white">
                        Tu volumen de entrenamiento
                    </h2>
                    <button
                        onClick={() => setShowVolumeInfo(!showVolumeInfo)}
                        className="p-1.5 rounded-full text-[#737373] hover:text-white hover:bg-white/10 transition-colors"
                        title="¿Qué es el volumen?"
                    >
                        <InfoIcon size={16} />
                    </button>
                </div>
                {showVolumeInfo && (
                    <p className="text-sm text-[#a3a3a3] mt-2 p-3 bg-[#252525] rounded-lg border border-[#3f3f3f]">
                        {VOLUME_INFO}
                    </p>
                )}
                <div className="mt-4">
                    <label className="block text-xs text-[#737373] mb-2">Fuente de volumen</label>
                    <select
                        value={volumeSystem ?? 'kpnk'}
                        onChange={e => handleSelectChange(e.target.value as VolumeSystem)}
                        className="w-full bg-[#252525] border border-[#3f3f3f] rounded-xl px-4 py-3 text-sm font-medium text-white focus:border-[#525252] outline-none"
                    >
                        <option value="israetel">Israetel</option>
                        <option value="kpnk">Personalizado (según tu perfil)</option>
                        <option value="manual">Manual</option>
                    </select>
                    {volumeSystem === 'israetel' && (
                        <p className="text-xs text-[#737373] mt-2 flex items-center gap-1.5">
                            <button type="button" onClick={() => setShowIsraetelInfo(!showIsraetelInfo)} className="text-[#a3a3a3] hover:text-white hover:underline">
                                <InfoIcon size={12} />
                            </button>
                            Rangos para la mayoría
                        </p>
                    )}
                    {showIsraetelInfo && volumeSystem === 'israetel' && (
                        <p className="text-sm text-[#a3a3a3] mt-2 p-3 bg-[#252525] rounded-lg border border-[#3f3f3f]">{ISRAETEL_INFO}</p>
                    )}
                </div>
            </div>

            <div className="flex-1 overflow-y-auto p-6 custom-scrollbar">
                {volumeSystem === 'kpnk' && (
                    <div className="max-w-lg mx-auto">
                        <p className="text-sm text-[#a3a3a3] mb-4">Responde unas preguntas y te recomendamos tu volumen según tu perfil.</p>
                        <button
                            onClick={() => setView('kpnk-wizard')}
                            className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm flex items-center justify-center gap-2"
                        >
                            Empezar cuestionario
                            <ChevronRightIcon size={18} />
                        </button>
                    </div>
                )}
                {volumeSystem === 'manual' && (
                    <div>
                        <p className="text-sm text-[#a3a3a3] mb-4">Indica tus series por semana para cada grupo.</p>
                        {volumeRecommendations.map(rec => (
                            <div key={rec.muscleGroup} className="mb-4 p-4 bg-[#252525] rounded-xl border border-[#3f3f3f]">
                                <p className="text-sm font-bold text-white mb-3">{rec.muscleGroup}</p>
                                <div className="grid grid-cols-3 gap-2">
                                    <div>
                                        <label className="text-[9px] text-zinc-500 uppercase">Mínimo</label>
                                        <input
                                            type="number"
                                            min={0}
                                            max={30}
                                            value={rec.minEffectiveVolume}
                                            onChange={e => handleUpdateManualRec(rec.muscleGroup, 'minEffectiveVolume', parseInt(e.target.value, 10) || 0)}
                                            className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white mt-1"
                                        />
                                    </div>
                                    <div>
                                        <label className="text-[9px] text-zinc-500 uppercase">Objetivo</label>
                                        <input
                                            type="number"
                                            min={1}
                                            max={30}
                                            value={Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2)}
                                            onChange={e => {
                                                const v = parseInt(e.target.value, 10) || rec.maxAdaptiveVolume;
                                                handleUpdateManualRec(rec.muscleGroup, 'maxAdaptiveVolume', v);
                                            }}
                                            className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white mt-1"
                                        />
                                    </div>
                                    <div>
                                        <label className="text-[9px] text-zinc-500 uppercase">Máximo</label>
                                        <input
                                            type="number"
                                            min={1}
                                            max={40}
                                            value={rec.maxRecoverableVolume}
                                            onChange={e => handleUpdateManualRec(rec.muscleGroup, 'maxRecoverableVolume', parseInt(e.target.value, 10) || 20)}
                                            className="w-full bg-black/50 border border-white/20 rounded px-2 py-1.5 text-xs text-white mt-1"
                                        />
                                    </div>
                                </div>
                            </div>
                        ))}
                        <button
                            onClick={handleManualConfirm}
                            className="w-full mt-6 py-4 bg-white text-[#1a1a1a] font-medium text-sm"
                        >
                            Continuar
                        </button>
                    </div>
                )}
                {(volumeSystem === 'israetel' || volumeSystem === null) && (
                    <div className="max-w-lg mx-auto">
                        <p className="text-sm text-[#a3a3a3] mb-4">Rangos publicados que sirven para la mayoría.</p>
                        <button
                            onClick={handleSelectIsraetel}
                            className="w-full py-4 bg-white text-[#1a1a1a] font-medium text-sm flex items-center justify-center gap-2"
                        >
                            Usar estos rangos y continuar
                            <ChevronRightIcon size={18} />
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};
