// components/wizard/WizardStepper.tsx
// Stepper horizontal con labels para wizards. Estética Cyber/NERD.

import React from 'react';

export interface WizardStepperProps {
    currentStep: number;
    totalSteps: number;
    /** Labels opcionales por paso (1-indexed). Por defecto "1", "2", "3"... */
    labels?: string[];
    className?: string;
    /** Paleta Tú (emerald) para NutritionWizard */
    variant?: 'default' | 'emerald';
}

const stepAccent = {
    default: { active: 'bg-cyber-cyan/20 border-cyber-cyan/50 text-cyber-cyan', line: 'bg-cyber-cyan/50' },
    emerald: { active: 'bg-emerald-500/20 border-emerald-500/50 text-emerald-400', line: 'bg-emerald-500/50' },
};

export const WizardStepper: React.FC<WizardStepperProps> = ({
    currentStep,
    totalSteps,
    labels,
    className = '',
    variant = 'default',
}) => {
    const acc = stepAccent[variant];
    const steps = Array.from({ length: totalSteps }, (_, i) => i);
    return (
        <div className={`flex items-center justify-center gap-2 ${className}`}>
            {steps.map((step, idx) => {
                const isActive = currentStep === step;
                const isCompleted = currentStep > step;
                const label = labels?.[idx] ?? String(step + 1);
                return (
                    <React.Fragment key={step}>
                        <div
                            className={`flex items-center justify-center w-8 h-8 rounded-full text-[10px] font-black uppercase transition-all border ${
                                isActive
                                    ? `${acc.active}`
                                    : isCompleted
                                        ? 'bg-white/10 border-white/20 text-white'
                                        : 'bg-transparent border-[#E6E0E9] text-[#49454F]'
                            }`}
                            aria-current={isActive ? 'step' : undefined}
                        >
                            {label}
                        </div>
                        {idx < totalSteps - 1 && (
                            <div
                                className={`w-6 h-px ${isCompleted ? acc.line : 'bg-white/10'}`}
                                aria-hidden
                            />
                        )}
                    </React.Fragment>
                );
            })}
        </div>
    );
};
