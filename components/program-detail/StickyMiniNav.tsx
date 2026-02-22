import React from 'react';
import { CalendarIcon, ActivityIcon, SettingsIcon, DumbbellIcon } from '../icons';

interface NavItem {
    id: string;
    label: string;
    icon: React.ReactNode;
}

const NAV_ITEMS: NavItem[] = [
    { id: 'training', label: 'Training', icon: <DumbbellIcon size={14} /> },
    { id: 'structure', label: 'Estructura', icon: <ActivityIcon size={14} /> },
    { id: 'metrics', label: 'Métricas', icon: <ActivityIcon size={14} /> },
    { id: 'events', label: 'Eventos', icon: <CalendarIcon size={14} /> },
    { id: 'config', label: 'Config', icon: <SettingsIcon size={14} /> },
];

interface StickyMiniNavProps {
    onScrollTo: (sectionId: string) => void;
}

const StickyMiniNav: React.FC<StickyMiniNavProps> = ({ onScrollTo }) => {
    return (
        <div className="sticky top-0 z-50 bg-black/95 backdrop-blur-xl border-b border-white/5 shadow-[0_10px_20px_rgba(0,0,0,0.8)]">
            <div className="flex items-center justify-center gap-1 px-2 py-2 overflow-x-auto no-scrollbar">
                {NAV_ITEMS.map(item => (
                    <button
                        key={item.id}
                        onClick={() => onScrollTo(item.id)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[8px] font-black uppercase tracking-widest text-zinc-500 hover:text-white hover:bg-zinc-900 transition-all shrink-0"
                    >
                        {item.icon}
                        <span className="hidden sm:inline">{item.label}</span>
                    </button>
                ))}
            </div>
        </div>
    );
};

export default StickyMiniNav;
