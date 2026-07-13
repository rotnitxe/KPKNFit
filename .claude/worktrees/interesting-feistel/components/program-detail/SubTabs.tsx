import React from 'react';
import { motion } from 'framer-motion';

export type StructureSubTab = 'semana' | 'macrociclo' | 'split';
export type AnalyticsSubTab = 'volumen' | 'progreso' | 'historiales';

interface SubTabConfig {
    label: string;
    value: string;
}

interface SubTabsProps {
    tabs: SubTabConfig[];
    activeTab: string;
    onChange: (tab: string) => void;
    variant?: 'structure' | 'analytics';
}

const SubTabs: React.FC<SubTabsProps> = ({ tabs, activeTab, onChange, variant = 'structure' }) => {
    return (
        <div className="px-4 mb-0">
            <div className="flex items-center gap-0.5 bg-zinc-900/5 backdrop-blur-sm border-b border-zinc-200/60 pt-2 pb-0 w-full">
                {tabs.map((tab, idx) => {
                    const isActive = activeTab === tab.value;
                    return (
                        <button
                            key={tab.value}
                            onClick={() => onChange(tab.value)}
                            className={`
                                flex-1 py-2.5 text-[9px] font-black uppercase tracking-[0.2em] transition-all relative
                                ${isActive 
                                    ? 'text-black' 
                                    : 'text-zinc-400 hover:text-zinc-600'
                                }
                            `}
                        >
                            {isActive && (
                                <motion.div
                                    layoutId={`subtab-indicator-${variant}`}
                                    transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                                    className="absolute bottom-0 left-0 right-0 h-0.5 bg-black rounded-full"
                                />
                            )}
                            <span>{tab.label}</span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
};

export default SubTabs;
