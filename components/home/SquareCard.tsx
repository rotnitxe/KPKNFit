// components/home/SquareCard.tsx
// Material 3 — Base card for grid layout (light theme)

import React from 'react';

interface SquareCardProps {
    children: React.ReactNode;
    onClick?: () => void;
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
            className="w-full h-full min-h-0 flex flex-col items-center justify-center p-3 bg-white rounded-[20px] shadow-[0_1px_3px_rgba(0,0,0,0.06),0_2px_8px_rgba(0,0,0,0.03)] hover:shadow-[0_2px_12px_rgba(0,0,0,0.08)] active:scale-[0.98] transition-all text-center overflow-hidden"
        >
            {isEmpty && emptyLabel ? (
                <span className="text-[10px] text-[#79747E] text-center leading-relaxed">{emptyLabel}</span>
            ) : (
                children
            )}
        </button>
    );
};
