// components/home/HomeCardsSection.tsx
// Material 3 — Secciones de tarjetas: Progreso físico y Progreso por ejercicio

import React, { useMemo } from 'react';
import { SquareCardsCarousel } from './SquareCardsCarousel';
import { SquareCard } from './SquareCard';
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

const SectionHeader: React.FC<{ title: string }> = ({ title }) => (
    <div className="flex items-center gap-2 mb-3">
        <h2 className="text-[18px] font-bold text-[#1C1B1F]">{title}</h2>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#1C1B1F" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M12 5l7 7-7 7" /></svg>
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
        <BodyMeasuresCard key="measures" onNavigate={() => onNavigateToCard('body-measures')} />,
        <FFMIBMICard key="ffmi" onNavigate={() => onNavigateToCard('ffmi-imc')} />,
        <EvolutionCard key="evo" onNavigate={() => onNavigateToCard('evolution')} />,
        <CaloriesHistoryCard key="cal" onNavigate={() => onNavigateToCard('calories-history')} />,
    ], [onNavigateToCard]);

    return (
        <div className="space-y-8">
            {/* Progreso físico y alimentación */}
            <section>
                <SectionHeader title="Progreso físico y alimentación" />
                <div className="mb-3">
                    <MacrosWidgetCard onNavigate={() => onNavigateToCard('macros')} />
                </div>
                <div className="grid grid-cols-2 gap-3">
                    {fisicoCards.map((card, i) => (
                        <div key={i} className="min-h-[72px] aspect-[1.2/1]">
                            {card}
                        </div>
                    ))}
                </div>
            </section>

            {/* Progreso en ejercicios */}
            <section>
                <SectionHeader title="Progreso en ejercicios" />
                <SquareCardsCarousel gap={14}>
                    {ejercicioCards}
                </SquareCardsCarousel>
            </section>
        </div>
    );
};
