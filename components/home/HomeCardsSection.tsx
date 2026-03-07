// components/home/HomeCardsSection.tsx
import React, { useMemo } from 'react';
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
import { ChevronRightIcon } from 'lucide-react';

interface HomeCardsSectionProps {
    onNavigateToCard: (cardType: string) => void;
}

export const SectionHeader: React.FC<{ title: string }> = ({ title }) => (
    <div className="w-full h-12 px-4 inline-flex justify-between items-center bg-[#FEF7FF]">
        <div className="text-[#1D1B20] text-[22px] font-normal font-['Roboto'] leading-tight">{title}</div>
        <div className="w-12 h-12 flex justify-center items-center text-[#49454F]">
            <ChevronRightIcon size={24} />
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
        <div className="w-full bg-[#FEF7FF] flex flex-col justify-start items-start">

            {/* Progreso Físico */}
            <div className="w-full bg-[#FEF7FF] flex flex-col justify-start items-start">
                <SectionHeader title="Progreso físico y alimentación" />

                {/* Calorías y Macros */}
                <div className="w-full px-4 pt-2 pb-4 flex flex-col gap-3">
                    <MacrosWidgetCard onNavigate={() => onNavigateToCard('macros')} />
                </div>

                {/* Grid Cards Scroll */}
                <div className="w-full px-4 py-2 flex justify-start items-start gap-1.5 overflow-x-auto snap-x pb-4 border-none outline-none ring-0">
                    {fisicoCards.map((card, i) => (
                        card
                    ))}
                </div>
            </div>

            {/* Progreso en ejercicios */}
            <div className="w-full bg-[#FEF7FF] flex flex-col justify-start items-start mt-2">
                <SectionHeader title="Progreso en ejercicios" />

                <div className="w-full px-4 pt-2 flex justify-start items-start gap-1.5 overflow-x-auto snap-x pb-6 border-none outline-none ring-0">
                    {ejercicioCards.map((card, i) => (
                        card
                    ))}
                </div>
            </div>

        </div>
    );
};
