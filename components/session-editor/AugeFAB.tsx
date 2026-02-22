import React from 'react';
import { ActivityIcon } from '../icons';

interface AugeFABProps {
    alertCount: number;
    hasWarnings: boolean;
    hasCritical: boolean;
    onClick: () => void;
}

const AugeFAB: React.FC<AugeFABProps> = ({ alertCount, hasWarnings, hasCritical, onClick }) => {
    const badgeColor = hasCritical ? 'bg-[#FF3B30]' : hasWarnings ? 'bg-[#FFD60A]' : 'bg-[#00F19F]';
    const glowColor = hasCritical
        ? 'shadow-[0_0_20px_rgba(255,59,48,0.3)]'
        : hasWarnings
            ? 'shadow-[0_0_20px_rgba(255,214,10,0.2)]'
            : '';

    return (
        <button
            onClick={onClick}
            className={`fixed bottom-20 right-4 z-30 w-12 h-12 rounded-full bg-[#111] border border-white/[0.08] flex items-center justify-center text-white hover:bg-[#1a1a1a] transition-all ${glowColor}`}
        >
            <ActivityIcon size={20} className="text-[#999]" />
            {alertCount > 0 && (
                <span className={`absolute -top-1 -right-1 w-5 h-5 rounded-full ${badgeColor} text-[9px] font-bold text-black flex items-center justify-center ${hasCritical ? 'animate-pulse' : ''}`}>
                    {alertCount > 9 ? '9+' : alertCount}
                </span>
            )}
        </button>
    );
};

export default AugeFAB;
