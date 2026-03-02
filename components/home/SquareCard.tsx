// components/home/SquareCard.tsx
// Base para tarjetas cuadradas del Home

import React from 'react';

interface SquareCardProps {
    children: React.ReactNode;
    onClick?: () => void;
    /** CTA cuando no hay datos */
    emptyLabel?: string;
    isEmpty?: boolean;
}

export const SquareCard: React.FC<SquareCardProps> = ({
    children,
    onClick,
    emptyLabel,
    isEmpty,
}) => {
    return (
        <button
            type="button"
            onClick={onClick}
            className="w-full h-full min-h-0 flex flex-col items-center justify-center p-2.5 bg-[#0a0a0a] border border-white/10 rounded-xl hover:border-white/20 active:bg-white/[0.02] transition-colors text-left overflow-hidden"
        >
            {isEmpty && emptyLabel ? (
                <span className="text-[9px] text-zinc-500 text-center">{emptyLabel}</span>
            ) : (
                children
            )}
        </button>
    );
};
