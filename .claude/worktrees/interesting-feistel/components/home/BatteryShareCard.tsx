// components/home/BatteryShareCard.tsx
// Tarjeta dedicada para compartir la batería AUGE — 3 anillos + logo KPKN + marco estilo Spotify

import React from 'react';

const RING_RADIUS = 58;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

const RING_COLORS = {
    cns: '#7c8aa1',
    muscular: '#9c8b7a',
    spinal: '#5c6b7d',
} as const;

export interface BatteryShareCardProps {
    cns: number;
    muscular: number;
    spinal: number;
}

export const BatteryShareCard: React.FC<BatteryShareCardProps> = ({ cns, muscular, spinal }) => {
    const rings: { cx: number; cy: number; value: number; label: string; color: string }[] = [
        { cx: 95, cy: 85, value: cns, label: 'SNC', color: RING_COLORS.cns },
        { cx: 225, cy: 85, value: muscular, label: 'Muscular', color: RING_COLORS.muscular },
        { cx: 160, cy: 165, value: spinal, label: 'Columna', color: RING_COLORS.spinal },
    ];

    return (
        <div
            id="battery-share-card"
            className="w-[540px] h-[960px] flex flex-col items-center justify-center font-sans overflow-hidden"
            style={{ backgroundColor: '#0a0a0a' }}
        >
            {/* Logo y mensaje — fuera de la tarjeta, centrados arriba */}
            <div className="flex flex-col items-center justify-center gap-3 shrink-0 py-12">
                <img
                    src="/kpkn-logo.png"
                    alt="KPKN"
                    className="w-20 h-20 object-contain"
                    loading="eager"
                    decoding="sync"
                />
                <p className="text-[11px] font-black text-white/80 uppercase tracking-[0.2em]">
                    ENTRENA CON KPKN
                </p>
            </div>

            {/* Tarjeta compacta con los anillos — más marco alrededor */}
            <div className="w-full flex-1 flex items-center justify-center px-14 py-8">
                <div
                    className="relative w-full max-w-[320px] flex flex-col items-center justify-center rounded-[20px] overflow-hidden border border-white/[0.12] shadow-xl shrink-0"
                    style={{
                        backgroundColor: '#121212',
                        boxShadow: '0 0 0 1px rgba(255,255,255,0.06), 0 25px 50px -12px rgba(0,0,0,0.5)',
                        minHeight: 420,
                    }}
                >
                    {/* Título */}
                    <p className="text-[10px] font-black text-[#49454F] uppercase tracking-[0.25em] mb-2 mt-5 shrink-0">
                        Tu Batería AUGE
                    </p>

                    {/* Los 3 anillos — tarjeta más compacta */}
                    <div className="relative flex items-center justify-center w-full flex-1" style={{ minHeight: 320 }}>
                        <svg viewBox="0 0 320 250" className="block w-full h-full max-h-[340px] min-h-[300px]" preserveAspectRatio="xMidYMid meet">
                            <defs>
                                {/* Resplandor sutil */}
                                <filter id="ringGlow" x="-50%" y="-50%" width="200%" height="200%">
                                    <feGaussianBlur stdDeviation="2" result="blur" />
                                    <feMerge>
                                        <feMergeNode in="blur" />
                                        <feMergeNode in="SourceGraphic" />
                                    </feMerge>
                                </filter>
                                {/* Sombra sutil */}
                                <filter id="ringShadow" x="-40%" y="-40%" width="180%" height="180%">
                                    <feDropShadow dx="0" dy="2" stdDeviation="4" floodOpacity="0.2" floodColor="#000" />
                                </filter>
                            </defs>
                            {rings.map(({ cx, cy, value, label, color }) => {
                                const dashArray = `${(value / 100) * RING_CIRCUMFERENCE} ${RING_CIRCUMFERENCE}`;
                                return (
                                    <g key={label} transform={`translate(${cx}, ${cy}) rotate(-90)`} filter="url(#ringShadow)">
                                        {/* Sombra del anillo de fondo */}
                                        <circle r={RING_RADIUS} fill="none" stroke="#0a0a0a" strokeWidth="8" opacity="0.35" />
                                        {/* Anillo de fondo */}
                                        <circle r={RING_RADIUS} fill="none" stroke="#27272a" strokeWidth="6" />
                                        {/* Anillo de progreso con resplandor */}
                                        <circle
                                            r={RING_RADIUS}
                                            fill="none"
                                            stroke={color}
                                            strokeWidth="6"
                                            strokeDasharray={dashArray}
                                            strokeLinecap="round"
                                            filter="url(#ringGlow)"
                                        />
                                        <g transform="rotate(90)">
                                            <foreignObject x={-38} y={-18} width={76} height={36} style={{ overflow: 'visible' }}>
                                                <div
                                                    style={{
                                                        width: '100%',
                                                        height: '100%',
                                                        display: 'flex',
                                                        flexDirection: 'column',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        fontFamily: 'system-ui, -apple-system, sans-serif',
                                                        fontWeight: 700,
                                                        fontSize: 18,
                                                        color: '#e4e4e7',
                                                        letterSpacing: '0.02em',
                                                        textShadow: '0 1px 2px rgba(0,0,0,0.3)',
                                                    }}
                                                >
                                                    <span style={{ lineHeight: 1.2 }}>{value}%</span>
                                                    <span style={{ fontSize: 11, color: '#71717a', lineHeight: 1.3, marginTop: 2 }}>{label}</span>
                                                </div>
                                            </foreignObject>
                                        </g>
                                    </g>
                                );
                            })}
                        </svg>
                    </div>
                </div>
            </div>
        </div>
    );
};
