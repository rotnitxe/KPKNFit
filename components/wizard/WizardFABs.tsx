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
    /** Paleta Tú (emerald) para NutritionWizard; por defecto cyber-cyan */
    variant?: 'default' | 'emerald';
}

const FAB_BOTTOM = 'max(1.5rem, env(safe-area-inset-bottom))';

const accentClasses = {
    default: {
        back: 'focus:ring-cyber-cyan',
        next: 'border-cyber-cyan/50 bg-cyber-cyan/20 text-cyber-cyan hover:bg-cyber-cyan/30 focus:ring-cyber-cyan',
    },
    emerald: {
        back: 'focus:ring-emerald-500',
        next: 'border-emerald-500/50 bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500/30 focus:ring-emerald-500',
    },
};

export const WizardFABs: React.FC<WizardFABsProps> = ({
    onBack,
    onNext,
    isLast = false,
    disabled = false,
    nextLabel,
    className = '',
    variant = 'default',
}) => {
    const acc = accentClasses[variant];
    return (
        <>
            {onBack && (
                <button
                    onClick={onBack}
                    aria-label="Atrás"
                    className={`absolute z-20 w-12 h-12 rounded-full border border-white/20 bg-black/40 text-[#49454F] hover:text-white hover:bg-black/60 flex items-center justify-center backdrop-blur-sm transition-all left-6 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${acc.back} ${className}`}
                    style={{ bottom: FAB_BOTTOM }}
                >
                    <ChevronLeftIcon size={22} />
                </button>
            )}

            <button
                onClick={onNext}
                disabled={disabled}
                aria-label={isLast ? (nextLabel || 'Guardar') : 'Siguiente'}
                className={`absolute z-20 w-12 h-12 rounded-full border flex items-center justify-center shadow-lg transition-all right-6 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-[#0a0a0a] ${acc.next} ${className}`}
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
