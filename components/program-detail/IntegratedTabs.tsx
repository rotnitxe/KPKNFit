import React from 'react';
import { motion } from 'framer-motion';

export type MainTab = 'training' | 'analytics';
export type StructureSubTab = 'semana' | 'split' | 'macrociclo';
export type AnalyticsSubTab = 'volumen' | 'progreso' | 'historiales';

interface IntegratedTabsProps {
    activeMainTab: MainTab;
    onMainTabChange: (tab: MainTab) => void;
    activeSubTab: StructureSubTab | AnalyticsSubTab;
    onSubTabChange: (tab: StructureSubTab | AnalyticsSubTab) => void;
    gradientTheme: string;
}

const IntegratedTabs: React.FC<IntegratedTabsProps> = ({
    activeMainTab,
    onMainTabChange,
    activeSubTab,
    onSubTabChange,
    gradientTheme,
}) => {
    const isTraining = activeMainTab === 'training';
    
    // Determine subtab options based on main tab
    const subTabs = isTraining
        ? [{ label: 'Semana', value: 'semana' }, { label: 'Split', value: 'split' }, { label: 'Macrociclo', value: 'macrociclo' }]
        : [{ label: 'Volumen', value: 'volumen' }, { label: 'Progreso', value: 'progreso' }, { label: 'Historiales', value: 'historiales' }];

    return (
        <div className="px-0 mb-0">
            {/* Main Tabs - Liquid Glass con gradiente */}
            <div className="px-4 pb-0">
                <div className="flex items-center gap-1.5 bg-white/40 backdrop-blur-xl border border-white/50 rounded-3xl p-1.5 w-full shadow-[0_4px_16px_rgba(0,0,0,0.06)]">
                    <button
                        onClick={() => onMainTabChange('training')}
                        className={`
                            flex-1 py-3 rounded-2xl text-[10px] font-black uppercase tracking-[0.25em] transition-all relative
                            ${activeMainTab === 'training' 
                                ? 'text-white shadow-lg' 
                                : 'text-zinc-500 hover:text-zinc-700'
                            }
                        `}
                    >
                        {activeMainTab === 'training' && (
                            <motion.div
                                layoutId="main-tab-bg"
                                transition={{ type: "spring", stiffness: 350, damping: 30 }}
                                className="absolute inset-0 bg-gradient-to-br from-purple-500 to-purple-600 rounded-2xl"
                                style={{ zIndex: 0 }}
                            />
                        )}
                        <span className="relative z-10">Estructura</span>
                    </button>
                    <button
                        onClick={() => onMainTabChange('analytics')}
                        className={`
                            flex-1 py-3 rounded-2xl text-[10px] font-black uppercase tracking-[0.25em] transition-all relative
                            ${activeMainTab === 'analytics' 
                                ? 'text-white shadow-lg' 
                                : 'text-zinc-500 hover:text-zinc-700'
                            }
                        `}
                    >
                        {activeMainTab === 'analytics' && (
                            <motion.div
                                layoutId="main-tab-bg"
                                transition={{ type: "spring", stiffness: 350, damping: 30 }}
                                className="absolute inset-0 bg-gradient-to-br from-cyan-500 to-blue-600 rounded-2xl"
                                style={{ zIndex: 0 }}
                            />
                        )}
                        <span className="relative z-10">Analíticas</span>
                    </button>
                </div>
            </div>

            {/* Sub Tabs - Extensión contextual */}
            <div className="px-4 pt-2 pb-1">
                <div className="flex items-center gap-1 bg-zinc-50/80 backdrop-blur-sm border border-zinc-200/60 rounded-2xl p-1 w-full">
                    {subTabs.map((tab) => {
                        const isActive = activeSubTab === tab.value;
                        return (
                            <button
                                key={tab.value}
                                onClick={() => onSubTabChange(tab.value as StructureSubTab | AnalyticsSubTab)}
                                className={`
                                    flex-1 py-2.5 rounded-xl text-[8.5px] font-black uppercase tracking-[0.2em] transition-all relative
                                    ${isActive
                                        ? 'text-zinc-900 bg-white shadow-sm'
                                        : 'text-zinc-400 hover:text-zinc-600'
                                    }
                                `}
                            >
                                {tab.label}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};

export default IntegratedTabs;
