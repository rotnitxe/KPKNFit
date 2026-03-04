// components/ui/Toast.tsx
import React, { useEffect, useState, useCallback, useRef } from 'react';
import { ToastData } from '../../types';
import { XIcon, InfoIcon, AlertTriangleIcon, CheckCircleIcon, TargetIcon } from '../icons';

interface ToastProps {
  toast: ToastData;
  onDismiss: (id: number) => void;
}

const ICONS = {
  success: <CheckCircleIcon size={18} className="text-emerald-400" />,
  achievement: <TargetIcon size={18} className="text-amber-400" />,
  suggestion: <InfoIcon size={18} className="text-sky-400" />,
  danger: <AlertTriangleIcon size={18} className="text-red-400" />,
};

const BORDER_COLORS = {
  success: 'border-emerald-500/50 bg-emerald-500/10',
  achievement: 'border-amber-500/50 bg-amber-500/10',
  suggestion: 'border-sky-500/50 bg-sky-500/10',
  danger: 'border-red-500/50 bg-red-500/10',
};

const Toast: React.FC<ToastProps> = ({ toast, onDismiss }) => {
  const [isExiting, setIsExiting] = useState(false);
  const [isMounting, setIsMounting] = useState(true);
  const [showWhy, setShowWhy] = useState(false);
  const whyRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setIsMounting(false);
  }, []);

  useEffect(() => {
    if (!showWhy) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (whyRef.current && !whyRef.current.contains(e.target as Node)) {
        setShowWhy(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showWhy]);

  const handleDismiss = useCallback(() => {
    setIsExiting(true);
    setTimeout(() => onDismiss(toast.id), 400);
  }, [onDismiss, toast.id]);

  useEffect(() => {
    const timer = setTimeout(() => {
      handleDismiss();
    }, toast.duration || 4000);

    return () => clearTimeout(timer);
  }, [toast.duration, handleDismiss]);

  const hasWhy = toast.type === 'danger' && toast.why;

  return (
    <div
      onClick={handleDismiss}
      className={`cursor-pointer w-auto min-w-[280px] max-w-[90vw] pointer-events-auto
                  transition-all duration-500 ease-out mb-3 origin-bottom
                  ${isMounting ? 'opacity-0 scale-95 translate-y-2 blur-[2px]' :
          isExiting ? 'opacity-0 scale-95 translate-x-4 blur-[2px]' : 'opacity-100 scale-100 translate-y-0 blur-0'}
      `}
    >
      <div className={`
          relative overflow-hidden
          flex items-start gap-3 px-4 py-3
          rounded-xl shadow-2xl
          bg-zinc-900 border ${BORDER_COLORS[toast.type]}
          group hover:scale-[1.02] active:scale-95 transition-transform
      `}>
        <div className="flex-shrink-0 mt-0.5">
          {ICONS[toast.type] || <InfoIcon size={18} className="text-zinc-400" />}
        </div>

        <div className="flex-grow flex flex-col justify-center min-w-0 pt-0.5">
          {toast.title && <span className="text-[9px] font-black text-white/50 uppercase tracking-[0.1em] mb-1">{toast.title}</span>}
          <span className="text-[13px] font-medium text-white/90 leading-snug">{toast.message}</span>

          {hasWhy && (
            <div ref={whyRef} className="relative mt-2">
              <button
                onClick={(e) => { e.stopPropagation(); setShowWhy(prev => !prev); }}
                className="text-[10px] font-bold text-red-400 hover:text-red-300 uppercase tracking-wider"
              >
                Ver más
              </button>
              {showWhy && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="absolute left-0 top-full mt-2 z-50 min-w-[240px] max-w-[85vw] p-3 rounded-lg bg-black/95 border border-red-500/20 shadow-xl"
                >
                  <p className="text-xs text-zinc-300 leading-relaxed">{toast.why}</p>
                </div>
              )}
            </div>
          )}
        </div>

        <button
          onClick={(e) => { e.stopPropagation(); handleDismiss(); }}
          aria-label="Cerrar"
          className="flex-shrink-0 text-zinc-500 hover:text-white transition-colors p-1"
        >
          <XIcon size={14} />
        </button>
      </div>
    </div>
  );
};

export default Toast;