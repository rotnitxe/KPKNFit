// components/ui/Toast.tsx
import React, { useEffect, useState, useCallback } from 'react';
import { ToastData } from '../../types';
import { CheckCircleIcon, XIcon } from '../icons';

interface ToastProps {
  toast: ToastData;
  onDismiss: (id: number) => void;
}

const toastIconClass = 'w-10 h-10 object-contain flex-shrink-0';

const CAUPOLICAN_ICONS = {
  suggestion: (
    <img src="/caupoNotificacion.svg" alt="" className={toastIconClass} aria-hidden />
  ),
  achievement: (
    <img src="/CaupolicanLogros.svg" alt="" className={toastIconClass} aria-hidden />
  ),
  danger: (
    <img src="/CaupolicanAlerta.svg" alt="" className={toastIconClass} aria-hidden />
  ),
};

const ICONS = {
    success: <CheckCircleIcon className="text-emerald-400" size={20} />,
    achievement: CAUPOLICAN_ICONS.achievement,
    suggestion: CAUPOLICAN_ICONS.suggestion,
    danger: CAUPOLICAN_ICONS.danger,
};

const GRADIENTS = {
    success: 'from-emerald-500/10 to-emerald-900/20 border-emerald-500/20',
    achievement: 'from-amber-500/15 to-yellow-900/20 border-amber-500/30 bg-gradient-to-r from-amber-500/12 to-[#1a1808]',
    suggestion: 'from-[#00F0FF]/15 to-[#00F0FF]/5 border-[#00F0FF]/30 bg-gradient-to-r from-[#00F0FF]/12 to-[#0a1628]',
    danger: 'from-red-500/15 to-red-900/20 border-red-500/30 bg-gradient-to-r from-red-500/12 to-[#1a0a0a]',
};

const Toast: React.FC<ToastProps> = ({ toast, onDismiss }) => {
  const [isExiting, setIsExiting] = useState(false);
  const [isMounting, setIsMounting] = useState(true);

  useEffect(() => {
    setIsMounting(false);
  }, []);

  const handleDismiss = useCallback(() => {
    setIsExiting(true);
    // Esperamos 400ms para que la animación de salida termine antes de removerlo del estado global
    setTimeout(() => onDismiss(toast.id), 400); 
  }, [onDismiss, toast.id]);

  useEffect(() => {
    // El temporizador ahora llama a handleDismiss() en lugar de onDismiss directo, 
    // para que también se vea la animación al irse solo.
    const timer = setTimeout(() => {
      handleDismiss();
    }, toast.duration || 4000);

    return () => clearTimeout(timer);
  }, [toast.duration, handleDismiss]); // Quitamos toast entero de las dependencias para evitar recargas fantasma

  return (
    <div
      onClick={handleDismiss}
      className={`cursor-pointer w-auto min-w-[300px] max-w-[90vw] pointer-events-auto
                  transition-all duration-500 cubic-bezier(0.19, 1, 0.22, 1) mb-3
                  ${isMounting ? 'opacity-0 scale-90 -translate-y-4' : 
                    isExiting ? 'opacity-0 scale-95 translate-x-4' : 'opacity-100 scale-100 translate-y-0'}
      `}
    >
      <div className={`
          relative overflow-hidden
          flex items-center gap-4 px-5 py-3.5
          rounded-sm
          bg-[#15171E] border border-[#2A2D38]
          shadow-[0_2px_8px_rgba(0,0,0,0.4)]
          group hover:scale-[1.02] active:scale-95 transition-transform
          ${GRADIENTS[toast.type]}
      `}>
          {/* Subtle Glow Background */}
          <div className="absolute inset-0 bg-gradient-to-r from-white/5 to-transparent opacity-50 pointer-events-none" />
          
          <div className="flex-shrink-0 drop-shadow-[0_0_10px_rgba(255,255,255,0.1)]">
              {ICONS[toast.type]}
          </div>

          <div className="flex-grow flex flex-col justify-center">
             {toast.title && <span className="text-[10px] font-black text-white/50 uppercase tracking-widest leading-none mb-1">{toast.title}</span>}
             <span className="text-sm font-bold text-white/90 leading-tight">{toast.message}</span>
          </div>

          <button 
             onClick={(e) => { e.stopPropagation(); handleDismiss(); }}
             className="flex-shrink-0 text-white/20 hover:text-white/60 transition-colors p-1 bg-black/20 rounded-full"
          >
              <XIcon size={14} />
          </button>
      </div>
    </div>
  );
};

export default Toast;