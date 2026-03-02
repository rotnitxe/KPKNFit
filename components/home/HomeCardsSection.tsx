// components/home/HomeCardsSection.tsx
// Sección de tarjetas cuadradas por categorías: Progreso e historial + Progreso físico

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
    /** Navegar a vista. cardType se mapea a View + data */
    onNavigateToCard: (cardType: string) => void;
}

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
        <div className="space-y-6">
            {/* Progreso físico: widget macros + grid 2x2 de tarjetas (todas visibles) */}
            <section>
                <h2 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 mb-2">
                    Progreso físico
                </h2>
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

            {/* Progreso e historial por ejercicio */}
            <section>
                <SquareCardsCarousel title="Progreso e historial por ejercicio" gap={14}>
                    {ejercicioCards}
                </SquareCardsCarousel>
            </section>
        </div>
    );
};
