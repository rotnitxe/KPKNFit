// components/nutrition/NutritionTooltip.tsx
// Tooltip educativo para conceptos de nutrición

import React, { useState, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';

interface NutritionTooltipProps {
    content: string;
    title?: string;
}

export const NutritionTooltip: React.FC<NutritionTooltipProps> = ({ content, title }) => {
    const [isVisible, setIsVisible] = useState(false);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const [style, setStyle] = useState<React.CSSProperties>({ opacity: 0, pointerEvents: 'none' });

    useEffect(() => {
        if (isVisible && triggerRef.current) {
            const triggerRect = triggerRef.current.getBoundingClientRect();
            const viewportPadding = 8;
            const tooltipWidth = 260;
            let top = triggerRect.top - viewportPadding;
            let transform = 'translateY(-100%)';
            if (triggerRect.top < 120) {
                top = triggerRect.bottom + viewportPadding;
                transform = 'none';
            }
            let left = triggerRect.left + (triggerRect.width / 2) - (tooltipWidth / 2);
            if (left < viewportPadding) left = viewportPadding;
            else if (left + tooltipWidth > window.innerWidth - viewportPadding) {
                left = window.innerWidth - tooltipWidth - viewportPadding;
            }
            setStyle({
                top: `${top}px`,
                left: `${left}px`,
                transform,
                opacity: 1,
                transition: 'opacity 0.2s',
                pointerEvents: 'auto',
            });
        } else {
            setStyle((prev) => ({ ...prev, opacity: 0, pointerEvents: 'none' }));
        }
    }, [isVisible]);

    const tooltipContent = (
        <div
            style={style}
            className="fixed p-3 bg-[#0a0a0a] border border-orange-500/30 rounded-lg shadow-xl z-[9999] text-xs text-zinc-300 w-[260px]"
            role="tooltip"
        >
            {title && <p className="font-bold text-orange-400 mb-1">{title}</p>}
            <p>{content}</p>
        </div>
    );

    return (
        <span className="inline-flex ml-1">
            <button
                ref={triggerRef}
                onMouseEnter={() => setIsVisible(true)}
                onMouseLeave={() => setIsVisible(false)}
                onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    setIsVisible((v) => !v);
                }}
                className="cursor-pointer text-zinc-500 hover:text-orange-400 transition-colors"
                aria-label="Información"
            >
                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="16" x2="12" y2="12" />
                    <line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
            </button>
            {ReactDOM.createPortal(tooltipContent, document.body)}
        </span>
    );
};
