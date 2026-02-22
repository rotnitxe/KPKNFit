import React from 'react';

interface FatigueIndicatorsProps {
    msc: number;
    snc: number;
    spinal: number;
}

const getColor = (value: number) => {
    if (value > 80) return '#FF3B30';
    if (value > 50) return '#FFD60A';
    return '#00F19F';
};

const FatigueIndicators: React.FC<FatigueIndicatorsProps> = ({ msc, snc, spinal }) => {
    return (
        <div className="flex items-center gap-1 shrink-0" title={`MSC: ${msc}% | SNC: ${snc}% | Espinal: ${spinal}%`}>
            <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: getColor(msc) }} />
            <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: getColor(snc) }} />
            <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: getColor(spinal) }} />
        </div>
    );
};

export default FatigueIndicators;
