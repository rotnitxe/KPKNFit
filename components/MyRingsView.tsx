// components/MyRingsView.tsx
// Vista dedicada exclusivamente a los RINGS - descanso, estrés, recuperación

import React, { useState, useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { AugeTelemetryPanel, RingsViewMode } from './home/AugeTelemetryPanel';
import { BrainIcon, ActivityIcon, MoonIcon, ZapIcon, TrendingUpIcon, InfoIcon } from './icons';

const MyRingsView: React.FC = () => {
    const { sleepLogs, dailyWellbeingLogs, settings } = useAppState();
    const [ringsView, setRingsView] = useState<RingsViewMode>('individual');
    const [expandedRing, setExpandedRing] = useState<'muscular' | 'cns' | 'spinal' | null>(null);

    // Calcular promedio de sueño
    const avgSleep = useMemo(() => {
        if (!sleepLogs || sleepLogs.length === 0) return null;
        const recent = sleepLogs.slice(0, 7);
        const total = recent.reduce((sum, log) => sum + (log.duration || 0), 0);
        return Math.round(total / recent.length);
    }, [sleepLogs]);

    // Calcular promedio de estrés
    const avgStress = useMemo(() => {
        if (!dailyWellbeingLogs || dailyWellbeingLogs.length === 0) return null;
        const recent = dailyWellbeingLogs.slice(0, 7);
        const total = recent.reduce((sum, log) => sum + (log.stressLevel || 0), 0);
        return Math.round(total / recent.length);
    }, [dailyWellbeingLogs]);

    // Calcular promedio de energía percibida (en el modelo actual se aproxima con motivation)
    const avgEnergy = useMemo(() => {
        if (!dailyWellbeingLogs || dailyWellbeingLogs.length === 0) return null;
        const recent = dailyWellbeingLogs.slice(0, 7);
        const total = recent.reduce((sum, log) => sum + (log.motivation || 0), 0);
        return Math.round(total / recent.length);
    }, [dailyWellbeingLogs]);

    return (
        <div className="min-h-full flex flex-col bg-[var(--md-sys-color-surface)] pb-8">
            {/* Hero Section con RINGS en fondo completo */}
            <div className="relative w-full overflow-hidden">
                {/* Background gradient */}
                <div className="absolute inset-0 bg-gradient-to-b from-[#ECE6F0]/80 via-white/60 to-transparent" />
                
                {/* Header */}
                <div className="relative px-6 pt-8 pb-4">
                    <div className="flex items-center gap-2 mb-2">
                        <ActivityIcon size={18} className="text-[#3F51B5]" />
                        <p className="text-[9px] font-black uppercase tracking-[0.2em] text-[#49454F]">Biometría en Tiempo Real</p>
                    </div>
                    <h1 className="text-[28px] font-black text-[#1D1B20] leading-tight">Mis RINGS</h1>
                    <p className="text-[12px] text-[#49454F] mt-1">Monitoreo de recuperación y estrés</p>
                </div>

                {/* RINGS en tamaño hero */}
                <div className="relative px-4 pb-6">
                    <AugeTelemetryPanel 
                        variant="hero" 
                        shareable 
                        viewMode={ringsView}
                    />
                </div>

                {/* Toggle de vista */}
                <div className="relative px-6 pb-4">
                    <div className="flex bg-[#ECE6F0] p-1 rounded-full w-fit mx-auto">
                        <button
                            onClick={() => setRingsView('rings')}
                            className={`px-4 py-2 rounded-full text-[10px] font-black uppercase tracking-wider transition-all ${
                                ringsView === 'rings'
                                    ? 'bg-white text-[#1D1B20] shadow-sm'
                                    : 'text-[#49454F]'
                            }`}
                        >
                            Combinado
                        </button>
                        <button
                            onClick={() => setRingsView('individual')}
                            className={`px-4 py-2 rounded-full text-[10px] font-black uppercase tracking-wider transition-all ${
                                ringsView === 'individual'
                                    ? 'bg-white text-[#1D1B20] shadow-sm'
                                    : 'text-[#49454F]'
                            }`}
                        >
                            Individual
                        </button>
                    </div>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="px-4 pb-6">
                <div className="grid grid-cols-3 gap-3">
                    {/* Sueño */}
                    <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-4 flex flex-col items-center">
                        <MoonIcon size={20} className="text-[#6750A4] mb-2" />
                        <span className="text-[20px] font-black text-[#1D1B20]">
                            {avgSleep ? `${avgSleep}h` : '--'}
                        </span>
                        <span className="text-[8px] font-black uppercase tracking-wider text-[#49454F] mt-0.5">Sueño prom.</span>
                    </div>

                    {/* Estrés */}
                    <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-4 flex flex-col items-center">
                        <BrainIcon size={20} className="text-[#B3261E] mb-2" />
                        <span className="text-[20px] font-black text-[#1D1B20]">
                            {avgStress ? `${avgStress}%` : '--'}
                        </span>
                        <span className="text-[8px] font-black uppercase tracking-wider text-[#49454F] mt-0.5">Estrés prom.</span>
                    </div>

                    {/* Energía */}
                    <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-4 flex flex-col items-center">
                        <ZapIcon size={20} className="text-[#006A6A] mb-2" />
                        <span className="text-[20px] font-black text-[#1D1B20]">
                            {avgEnergy ? `${avgEnergy}%` : '--'}
                        </span>
                        <span className="text-[8px] font-black uppercase tracking-wider text-[#49454F] mt-0.5">Energía prom.</span>
                    </div>
                </div>
            </div>

            {/* Información Expandida */}
            <div className="px-4 pb-8 space-y-3">
                <div className="flex items-center gap-2 mb-2">
                    <InfoIcon size={16} className="text-[#49454F]" />
                    <p className="text-[10px] font-black uppercase tracking-[0.15em] text-[#49454F]">Información de cada sistema</p>
                </div>

                {/* Sistema Muscular */}
                <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5">
                    <div className="flex items-center gap-2 mb-3">
                        <div className="w-3 h-3 rounded-full bg-[#3F51B5]" />
                        <h3 className="text-[13px] font-black text-[#1D1B20] uppercase tracking-wide">Sistema Muscular</h3>
                    </div>
                    <p className="text-[12px] text-[#49454F] leading-relaxed">
                        Indica qué tan recuperados están tus músculos después del entrenamiento. Un nivel bajo significa que tus fibras necesitan descansar para evitar sobrecargas y estar listas para tu próximo reto.
                    </p>
                    <div className="mt-3 flex items-center gap-2">
                        <TrendingUpIcon size={14} className="text-[#3F51B5]" />
                        <span className="text-[11px] font-medium text-[#3F51B5]">
                            Se recupera en 24-72 horas pos-esfuerzo
                        </span>
                    </div>
                </div>

                {/* Sistema Nervioso Central */}
                <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5">
                    <div className="flex items-center gap-2 mb-3">
                        <div className="w-3 h-3 rounded-full bg-[#00828E]" />
                        <h3 className="text-[13px] font-black text-[#1D1B20] uppercase tracking-wide">Sistema Nervioso Central</h3>
                    </div>
                    <p className="text-[12px] text-[#49454F] leading-relaxed">
                        Es tu 'batería' de energía mental y coordinación. Si está baja, puedes sentirte más lento de reflejos o con la mente cansada después de un día intenso o poco sueño.
                    </p>
                    <div className="mt-3 flex items-center gap-2">
                        <MoonIcon size={14} className="text-[#00828E]" />
                        <span className="text-[11px] font-medium text-[#00828E]">
                            Depende directamente de la calidad del sueño
                        </span>
                    </div>
                </div>

                {/* Sistema Espinal */}
                <div className="rounded-[24px] border border-black/[0.04] bg-white/70 backdrop-blur-xl p-5">
                    <div className="flex items-center gap-2 mb-3">
                        <div className="w-3 h-3 rounded-full bg-[#C62828]" />
                        <h3 className="text-[13px] font-black text-[#1D1B20] uppercase tracking-wide">Columna y Articulaciones</h3>
                    </div>
                    <p className="text-[12px] text-[#49454F] leading-relaxed">
                        Mide el impacto acumulado en tu espalda y articulaciones. Te ayuda a saber cuándo es mejor bajar la carga para cuidar tu estructura y evitar molestias articulares.
                    </p>
                    <div className="mt-3 flex items-center gap-2">
                        <ActivityIcon size={14} className="text-[#C62828]" />
                        <span className="text-[11px] font-medium text-[#C62828]">
                            Impacto acumulativo de cargas pesadas
                        </span>
                    </div>
                </div>
            </div>

            {/* Google Health Integration Placeholder */}
            <div className="px-4">
                <div className="rounded-[24px] border border-dashed border-[#006A6A]/30 bg-[#006A6A]/5 p-5 text-center">
                    <div className="w-12 h-12 rounded-full bg-[#006A6A]/10 flex items-center justify-center mx-auto mb-3">
                        <ActivityIcon size={24} className="text-[#006A6A]" />
                    </div>
                    <h3 className="text-[13px] font-black text-[#1D1B20] uppercase tracking-wide mb-1">
                        Google Health Connect
                    </h3>
                    <p className="text-[11px] text-[#49454F] mb-3">
                        Sincroniza tus datos de sueño, actividad y frecuencia cardíaca para una medición más precisa.
                    </p>
                    <button className="px-5 py-2.5 rounded-full bg-[#006A6A] text-white text-[10px] font-black uppercase tracking-wider opacity-50 cursor-not-allowed">
                        Próximamente
                    </button>
                </div>
            </div>
        </div>
    );
};

export default MyRingsView;
