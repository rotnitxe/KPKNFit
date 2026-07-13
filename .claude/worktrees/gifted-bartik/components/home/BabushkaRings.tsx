// components/home/BabushkaRings.tsx
import React from 'react';

interface BabushkaRingsProps {
    carbsPct: number; // 0 to 1
    proteinPct: number; // 0 to 1
    fatPct: number; // 0 to 1
    size?: number;
}

export const BabushkaRings: React.FC<BabushkaRingsProps> = ({
    carbsPct,
    proteinPct,
    fatPct,
    size = 120
}) => {
    const strokeWidth = 8;
    const spacing = 4;

    // Outer: Carbs
    const r1 = (size / 2) - strokeWidth;
    // Middle: Protein
    const r2 = r1 - strokeWidth - spacing;
    // Inner: Fat
    const r3 = r2 - strokeWidth - spacing;

    const renderRing = (r: number, pct: number, color: string, trackColor: string) => {
        const circumference = 2 * Math.PI * r;
        const offset = circumference - (Math.min(1, pct) * circumference);
        return (
            <g>
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={r}
                    fill="none"
                    stroke={trackColor}
                    strokeWidth={strokeWidth}
                    strokeLinecap="round"
                    className="opacity-10"
                />
                <circle
                    cx={size / 2}
                    cy={size / 2}
                    r={r}
                    fill="none"
                    stroke={color}
                    strokeWidth={strokeWidth}
                    strokeDasharray={circumference}
                    strokeDashoffset={offset}
                    strokeLinecap="round"
                    className="transition-all duration-1000 ease-out"
                    transform={`rotate(-90 ${size / 2} ${size / 2})`}
                />
            </g>
        );
    };

    return (
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
            {renderRing(r1, carbsPct, '#D0BCFF', '#ECE6F0')}
            {renderRing(r2, proteinPct, '#B3261E', '#ECE6F0')}
            {renderRing(r3, fatPct, '#006A6A', '#ECE6F0')}
        </svg>
    );
};
