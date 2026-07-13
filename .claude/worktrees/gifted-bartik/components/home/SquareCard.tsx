// components/home/SquareCard.tsx
import React from 'react';

interface SquareCardProps {
    children?: React.ReactNode;
    onClick?: () => void;
    label: string;
    emptyMessage?: string;
}

export const SquareCard: React.FC<SquareCardProps> = ({
    children,
    onClick,
    label,
    emptyMessage,
}) => {
    return (
        <div className="inline-flex flex-col justify-start items-start gap-1" onClick={onClick} style={{ cursor: onClick ? 'pointer' : 'default' }}>
            <div className="w-24 h-24 relative rounded-2xl overflow-hidden bg-[#ECE6F0] flex flex-col justify-center items-center p-2 hover:bg-black/5 transition-colors">
                {emptyMessage ? (
                    <span className="text-xs text-[#49454F] text-center leading-tight">{emptyMessage}</span>
                ) : children}
            </div>
            <div className="self-stretch justify-start text-[#1D1B20] text-sm font-medium font-['Roboto'] leading-5 tracking-tight line-clamp-2">
                {label}
            </div>
        </div>
    );
};
