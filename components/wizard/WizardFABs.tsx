// components/wizard/WizardFABs.tsx
// FABs reutilizables para avanzar/retroceder en wizards. Estética Cyber/NERD.

import React from 'react';
import { ChevronRightIcon, ChevronLeftIcon, CheckIcon } from '../icons';

export interface WizardFABsProps {
    onBack?: () => void;
    onNext: () => void;
    isLast?: boolean;
    disabled?: boolean;
    /** Etiqueta para el FAB de siguiente (ej. "Empezar", "Guardar") */
    nextLabel?: string;
    /** Clase adicional para el contenedor */
    className?: string;
}

const FAB_BOTTOM = 'max(1.5rem, env(safe-area-inset-bottom))';

export const WizardFABs: React.FC<WizardFABsProps> = ({
    onBack,
    onNext,
    isLast = false,
    disabled = false,
    nextLabel,
    className = '',
}) => {
    return (
        <>
            {onBack && (
                <button
                    onClick={onBack}
                    aria-label="Atrás"
                    className={`absolute z-20 w-12 h-12 rounded-full border border-white/20 bg-black/40 text-zinc-400 hover:text-white hover:bg-black/60 flex items-center justify-center backdrop-blur-sm transition-all left-6 focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${className}`}
                    style={{ bottom: FAB_BOTTOM }}
                >
                    <ChevronLeftIcon size={22} />
                </button>
            )}

            <button
                onClick={onNext}
                disabled={disabled}
                aria-label={isLast ? (nextLabel || 'Guardar') : 'Siguiente'}
                className={`absolute z-20 w-12 h-12 rounded-full border border-cyber-cyan/50 bg-cyber-cyan/20 text-cyber-cyan hover:bg-cyber-cyan/30 flex items-center justify-center shadow-lg transition-all right-6 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-cyber-cyan focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${className}`}
                style={{ bottom: FAB_BOTTOM }}
            >
                {isLast ? (
                    <CheckIcon size={22} strokeWidth={2.5} />
                ) : (
                    <ChevronRightIcon size={22} />
                )}
            </button>
        </>
    );
};
