import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom';
import { XIcon } from '../icons';
import TacticalBackdrop from './TacticalBackdrop';

export type TacticalVariant = 'default' | 'failure' | 'pr' | 'sheet';

interface TacticalModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  variant?: TacticalVariant;
  className?: string;
  useCustomContent?: boolean;
}

const TacticalModal: React.FC<TacticalModalProps> = ({
  isOpen,
  onClose,
  children,
  title,
  variant = 'default',
  className = '',
  useCustomContent = false,
}) => {
  const [show, setShow] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setShow(true);
      const handleKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape') onClose();
      };
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    } else {
      const timer = setTimeout(() => setShow(false), 200);
      return () => clearTimeout(timer);
    }
  }, [isOpen, onClose]);

  if (!isOpen && !show) return null;

  // White theme with Liquid Glass effect
  const borderClass =
    variant === 'failure'
      ? 'border-red-300 shadow-[0_0_40px_rgba(239,68,68,0.15)]'
      : variant === 'pr'
        ? 'border-amber-300 shadow-[0_0_40px_rgba(245,158,11,0.15)]'
        : 'border-slate-200 shadow-2xl';

  const isSheet = variant === 'sheet';

  return ReactDOM.createPortal(
    <div
      className={`fixed inset-0 z-[200] flex transition-opacity duration-200 ease-out
                  ${isSheet ? 'items-end justify-center' : 'items-center justify-center p-4 sm:p-6'}
                  ${isOpen ? 'opacity-100' : 'opacity-0'}`}
      role="dialog"
      aria-modal="true"
      aria-labelledby={title !== undefined ? 'tactical-modal-title' : undefined}
    >
      <TacticalBackdrop onClick={onClose} variant="modal" />

      <div
        className={`
          relative z-10 w-full tactical-modal-base
          flex flex-col
          transform transition-all duration-200 ease-out
          ${isSheet ? 'max-h-[90dvh] animate-slide-up' : 'max-w-md max-h-[85vh]'}
          ${!isSheet ? (isOpen ? 'animate-tactical-enter' : 'animate-tactical-exit opacity-0') : ''}
          ${borderClass}
          ${className}
          bg-white/95 backdrop-blur-2xl rounded-2xl
        `}
        style={isSheet ? { height: '90vh', maxHeight: '90dvh' } : undefined}
      >
        {title !== undefined && (
          <div className="flex items-center justify-between px-5 py-4 flex-shrink-0 border-b border-slate-200">
            <h2
              id="tactical-modal-title"
              className="text-base font-bold text-slate-800 font-sans text-left"
            >
              {title}
            </h2>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-cyan-500 focus:ring-offset-2 focus:ring-offset-white"
              aria-label="Cerrar"
            >
              <XIcon size={18} />
            </button>
          </div>
        )}

        {useCustomContent ? (
          <div className="flex-grow min-h-0 flex flex-col font-sans">
            {children}
          </div>
        ) : (
          <div
            className={`overflow-y-auto px-5 pb-6 custom-scrollbar font-sans ${
              title === undefined ? 'pt-6' : 'pt-5'
            }`}
          >
            {children}
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};

export default TacticalModal;
