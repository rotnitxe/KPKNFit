import React from 'react';
import { ActivityIcon } from '../icons';

export type AugeSessionStatus = 'optimal' | 'warning' | 'fatiguing';

interface AugeFABProps {
    alertCount: number;
    sessionStatus: AugeSessionStatus;
    hasCritical?: boolean;
    onClick: () => void;
}

const AugeFAB: React.FC<AugeFABProps> = ({ alertCount, sessionStatus, hasCritical, onClick }) => {
    const fabBg = sessionStatus === 'fatiguing' ? 'bg-[#FF3B30]/20 border-[#FF3B30]/50' : sessionStatus === 'warning' ? 'bg-[#FFD60A]/20 border-[#FFD60A]/50' : 'bg-[#00F19F]/20 border-[#00F19F]/50';
    const badgeColor = alertCount > 0 ? (hasCritical ? 'bg-[#FF3B30]' : 'bg-[#FFD60A]') : 'bg-[#00F19F]';
    const glowColor = sessionStatus === 'fatiguing'
        ? 'shadow-[0_0_20px_rgba(255,59,48,0.25)]'
        : sessionStatus === 'warning'
            ? 'shadow-[0_0_20px_rgba(255,214,10,0.2)]'
            : 'shadow-[0_0_15px_rgba(0,241,159,0.12)]';

    return (
        <button
            onClick={onClick}
            className={`fixed bottom-28 right-4 z-30 w-12 h-12 rounded-full ${fabBg} border flex items-center justify-center text-white hover:opacity-90 transition-all ${glowColor}`}
        >
            <ActivityIcon size={20} className="text-white" />
            {alertCount > 0 && (
                <span className={`absolute -top-1 -right-1 w-5 h-5 rounded-full ${badgeColor} text-[9px] font-bold text-black flex items-center justify-center ${hasCritical ? 'animate-pulse' : ''}`}>
                    {alertCount > 9 ? '9+' : alertCount}
                </span>
            )}
        </button>
    );
};

export default AugeFAB;
