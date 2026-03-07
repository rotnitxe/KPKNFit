// components/home/HomeCardsSection.tsx
// Material 3 — Home card sections matching Figma dev export exactly:
//   · Section headers: text-xl font-normal + chevron icon in rounded container
//   · Grid cards: w-24 h-24 with label below (text-sm font-medium)
//   · Exercise cards: horizontal scroll with w-24 h-24 images

import React, { useMemo } from 'react';
import { SquareCardsCarousel } from './SquareCardsCarousel';
import {
    ExerciseHistoryCard,
    Star1RMCard,
    RelativeStrengthCard,
    EventMarksCard,
    IPFGLCard,
} from './cards/ProgresoEjercicioCards';
import {
    MacrosWidgetCard,
    EvolutionCard,
    CaloriesHistoryCard,
    BodyMeasuresCard,
    FFMIBMICard,
} from './cards/ProgresoFisicoCards';

interface HomeCardsSectionProps {
    onNavigateToCard: (cardType: string) => void;
}

// Figma M3 section header: text-xl font-normal + chevron in rounded circular button
const SectionHeader: React.FC<{ title: string }> = ({ title }) => (
    <div className="self-stretch h-12 px-4 inline-flex justify-start items-center">
        <div className="text-[var(--md-sys-color-on-surface)] text-xl font-normal font-['Roboto'] leading-7">{title}</div>
        <div className="w-10 rounded-[100px] inline-flex flex-col justify-center items-center overflow-hidden">
            <div className="self-stretch h-10 inline-flex justify-center items-center">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M10 7l5 5-5 5" stroke="var(--md-sys-color-on-surface-variant)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </div>
        </div>
    </div>
);

export const HomeCardsSection: React.FC<HomeCardsSectionProps> = ({ onNavigateToCard }) => {
    const ejercicioCards = useMemo(() => [
        <ExerciseHistoryCard key="hist" onNavigate={() => onNavigateToCard('exercise-history')} />,
        <Star1RMCard key="star" onNavigate={() => onNavigateToCard('star-1rm')} />,
        <RelativeStrengthCard key="rel" onNavigate={() => onNavigateToCard('relative-strength')} />,
        <EventMarksCard key="evt" onNavigate={() => onNavigateToCard('event-marks')} />,
        <IPFGLCard key="ipf" onNavigate={() => onNavigateToCard('ipf-gl')} />,
    ], [onNavigateToCard]);

    const fisicoCards = useMemo(() => [
        <FFMIBMICard key="ffmi" onNavigate={() => onNavigateToCard('ffmi-imc')} />,
        <BodyMeasuresCard key="measures" onNavigate={() => onNavigateToCard('body-measures')} />,
        <EvolutionCard key="evo" onNavigate={() => onNavigateToCard('evolution')} />,
        <CaloriesHistoryCard key="cal" onNavigate={() => onNavigateToCard('calories-history')} />,
    ], [onNavigateToCard]);

    return (
        <div className="bg-[var(--md-sys-color-surface,#fff)]">
            {/* ═══ Progreso físico y alimentación ═══ */}
            <section>
                <SectionHeader title="Progreso físico y alimentación" />
                {/* Nutrition rows (Calorías + Macros) */}
                <div className="px-4 pb-2">
                    <MacrosWidgetCard onNavigate={() => onNavigateToCard('macros')} />
                </div>
                {/* Figma: 4 cards w-24 h-24 horizontal with text-sm labels below */}
                <div className="self-stretch px-4 py-2 inline-flex justify-start items-start gap-1.5 overflow-x-auto">
                    {fisicoCards.map((card, i) => (
                        <div key={i} className="inline-flex flex-col justify-start items-start gap-1">
                            <div className="w-24 h-24 relative rounded-2xl overflow-hidden">
                                {card}
                            </div>
                        </div>
                    ))}
                </div>
            </section>

            {/* ═══ Progreso en ejercicios ═══ */}
            <section className="pb-4">
                <SectionHeader title="Progreso en ejercicios" />
                <div className="self-stretch relative">
                    <div className="w-full pl-4 pb-4 inline-flex justify-start items-start gap-2 overflow-x-auto">
                        {ejercicioCards.map((card, i) => (
                            <div key={i} className="p-2 rounded-xl inline-flex flex-col justify-start items-start gap-2 overflow-hidden">
                                <div className="w-24 h-32 flex flex-col justify-start items-start gap-1">
                                    <div className="w-24 h-24 relative rounded-2xl overflow-hidden">
                                        {card}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>
        </div>
    );
};
