// components/ui/InfoTooltip.tsx
import React, { useState, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { TERMINOLOGY } from '../../data/terminology';
import { Terminology } from '../../types';

interface InfoTooltipProps {
  term: Terminology | string;
}

export const InfoTooltip: React.FC<InfoTooltipProps> = ({ term }) => {
  const [isVisible, setIsVisible] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [style, setStyle] = useState<React.CSSProperties>({ opacity: 0, pointerEvents: 'none' });

  const definition = TERMINOLOGY[term as keyof typeof TERMINOLOGY] || TERMINOLOGY[term.split(' ')[0] as keyof typeof TERMINOLOGY];
  
  // Effect to calculate position
  useEffect(() => {
    if (isVisible && triggerRef.current) {
      const triggerRect = triggerRef.current.getBoundingClientRect();
      const viewportPadding = 8;
      const tooltipHeightEstimate = 100; // An average height to decide if we should flip
      const tooltipWidth = 256; // w-64 from tailwind

      let top, transform;

      // Decide placement: top or bottom
      if (triggerRect.top < tooltipHeightEstimate + viewportPadding) {
        // Not enough space above, place below
        top = triggerRect.bottom + viewportPadding;
        transform = 'none';
      } else {
        // Place above
        top = triggerRect.top - viewportPadding;
        transform = 'translateY(-100%)';
      }

      // Calculate horizontal position and handle screen edges
      let left = triggerRect.left + (triggerRect.width / 2) - (tooltipWidth / 2);
      if (left < viewportPadding) {
        left = viewportPadding;
      } else if (left + tooltipWidth > window.innerWidth - viewportPadding) {
        left = window.innerWidth - tooltipWidth - viewportPadding;
      }
      
      setStyle({
        top: `${top}px`,
        left: `${left}px`,
        transform: transform,
        opacity: 1,
        transition: 'opacity 0.2s',
        pointerEvents: 'auto',
      });
    } else {
      // Hide the tooltip
      setStyle(prevStyle => ({ ...prevStyle, opacity: 0, pointerEvents: 'none' }));
    }
  }, [isVisible]);

  if (!definition) return null;

  const tooltipContent = (
    <div
      style={style}
      className="fixed p-3 bg-slate-950 border border-slate-700 rounded-lg shadow-xl z-[9999] text-sm text-slate-300 w-64"
      role="tooltip"
    >
      <p><strong className="text-white">{term}:</strong> {definition}</p>
    </div>
  );

  return (
    <div className="inline-flex items-center ml-1 z-30">
      <button 
        ref={triggerRef}
        onMouseEnter={() => setIsVisible(true)} 
        onMouseLeave={() => setIsVisible(false)}
        onClick={(e) => { e.preventDefault(); e.stopPropagation(); setIsVisible(v => !v); }}
        className="cursor-pointer text-slate-500 hover:text-primary-color"
        aria-label={`Información sobre ${term}`}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="16" x2="12" y2="12"></line>
          <line x1="12" y1="8" x2="12.01" y2="8"></line>
        </svg>
      </button>
      {ReactDOM.createPortal(tooltipContent, document.body)}
    </div>
  );
};