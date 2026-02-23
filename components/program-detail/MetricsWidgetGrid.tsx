import React from 'react';
import { Program, Session } from '../../types';
import { ChevronRightIcon, ActivityIcon, DumbbellIcon, TargetIcon, ZapIcon, BarChartIcon, FlameIcon, TrendingUpIcon } from '../icons';
import { useAppContext } from '../../contexts/AppContext';

export type MetricId = 'volume' | 'strength' | 'density' | 'frequency' | 'banister' | 'recovery' | 'adherence' | 'rpe';

interface MetricsWidgetGridProps {
    program: Program;
    history: any[];
    displayedSessions: Session[];
    totalAdherence: number;
    selectedWeekId: string | null;
    currentWeeks: { id: string; sessions: Session[] }[];
    weeklyAdherence: { weekName: string; pct: number }[];
    adaptiveCache?: any;
}

const WIDGETS: { id: MetricId; label: string; shortLabel: string; Icon: React.ComponentType<{ size?: number; className?: string }>; color: string }[] = [
    { id: 'volume', label: 'Volumen D/I', shortLabel: 'Volumen', Icon: BarChartIcon, color: 'from-emerald-500 to-teal-600' },
    { id: 'strength', label: 'Fuerza', shortLabel: 'Fuerza', Icon: DumbbellIcon, color: 'from-blue-500 to-indigo-600' },
    { id: 'density', label: 'Densidad', shortLabel: 'Densidad', Icon: ActivityIcon, color: 'from-violet-500 to-purple-600' },
    { id: 'frequency', label: 'Frecuencia', shortLabel: 'Frecuencia', Icon: TargetIcon, color: 'from-amber-500 to-cyber-cyan' },
    { id: 'banister', label: 'AUGE Banister', shortLabel: 'Banister', Icon: TrendingUpIcon, color: 'from-cyan-500 to-blue-600' },
    { id: 'recovery', label: 'Recuperación', shortLabel: 'Recup.', Icon: ZapIcon, color: 'from-green-500 to-emerald-600' },
    { id: 'adherence', label: 'Adherencia', shortLabel: 'Adherencia', Icon: FlameIcon, color: 'from-rose-500 to-pink-600' },
    { id: 'rpe', label: 'RPE/Intensidad', shortLabel: 'RPE', Icon: ActivityIcon, color: 'from-sky-500 to-cyan-600' },
];

const viewByMetric: Record<MetricId, string> = {
    volume: 'program-metric-volume',
    strength: 'program-metric-strength',
    density: 'program-metric-density',
    frequency: 'program-metric-frequency',
    banister: 'program-metric-banister',
    recovery: 'program-metric-recovery',
    adherence: 'program-metric-adherence',
    rpe: 'program-metric-rpe',
};

const MetricsWidgetGrid: React.FC<MetricsWidgetGridProps> = ({
    program, history, displayedSessions, totalAdherence, selectedWeekId, currentWeeks, weeklyAdherence, adaptiveCache,
}) => {
    const { navigateTo } = useAppContext();

    const handleWidgetClick = (metricId: MetricId) => {
        navigateTo(viewByMetric[metricId] as any, {
            programId: program.id,
            metricId,
            selectedWeekId,
            currentWeeks,
            weeklyAdherence,
            totalAdherence,
            adaptiveCache,
        });
    };

    return (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-3">
            {WIDGETS.map(({ id, label, shortLabel, Icon, color }) => (
                <button
                    key={id}
                    onClick={() => handleWidgetClick(id)}
                    className="flex flex-col items-start p-3 rounded-xl bg-[#1a1a1a] border border-white/5 hover:border-white/15 hover:bg-[#222] transition-all text-left group"
                >
                    <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${color} flex items-center justify-center mb-2`}>
                        <Icon size={14} className="text-white" />
                    </div>
                    <span className="text-[11px] font-bold text-white uppercase tracking-wide leading-tight">
                        {shortLabel}
                    </span>
                    <span className="text-[9px] text-[#8E8E93] mt-0.5 flex items-center gap-1">
                        Ver detalle
                        <ChevronRightIcon size={10} className="opacity-0 group-hover:opacity-100 transition-opacity" />
                    </span>
                </button>
            ))}
        </div>
    );
};

export default MetricsWidgetGrid;
