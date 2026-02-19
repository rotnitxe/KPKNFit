// components/PowerliftingView.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { Program, Session, ProgramWeek } from '../types';
import PowerliftingDashboard from './PowerliftingDashboard';
import WeeklyFatigueCard from './WeeklyFatigueCard';
import RelativeStrengthCard from './RelativeStrengthCard';
import OnThisDayCard from './OnThisDayCard';
import PersonalRecordsView from './PersonalRecordsView';
import CarpeDiemCoachCard from './CarpeDiemCoachCard';
import EffectiveVolumeCard from './EffectiveVolumeCard';
import InjuryRiskAlerts from './InjuryRiskAlerts';
import WeeklyProgressAnalysis from './WeeklyProgressAnalysis';
import IPFPointsCard from './IPFPointsCard';
import CurrentBlockCard from './CurrentBlockCard';
import WeeklyWellbeingCard from './WeeklyWellbeingCard';

interface NextSessionInfo {
    program: Program;
    session: Session;
    weekVariant?: ProgramWeek['variant'];
}

const PowerliftingView: React.FC = () => {
    const { history, skippedLogs, settings, isOnline, programs } = useAppState();

    const nextSessionInfo = useMemo<NextSessionInfo | null>(() => {
        const todayIndex = new Date().getDay(); // 0 = Sunday

        for (const program of programs) {
            for (const macro of program.macrocycles) {
                for (const block of (macro.blocks || [])) {
                    for (const meso of block.mesocycles) {
                        for (const week of meso.weeks) {
                            const session = week.sessions.find(s => s.dayOfWeek === todayIndex);
                            if (session) {
                                return { program, session, weekVariant: week.variant };
                            }
                        }
                    }
                }
            }
        }

        const lastLog = history.length > 0 ? history[history.length - 1] : null;
        if (lastLog) {
            const program = programs.find(p => p.id === lastLog.programId);
            if (program) {
                const allSessionsWithContext = program.macrocycles.flatMap(macro =>
                    (macro.blocks || []).flatMap(block =>
                        block.mesocycles.flatMap(meso =>
                            meso.weeks.flatMap(week =>
                                week.sessions.map(session => ({ session, program, weekVariant: week.variant }))
                            )
                        )
                    )
                );
                const lastLogIndex = allSessionsWithContext.findIndex(item => item.session.id === lastLog.sessionId);
                if (lastLogIndex > -1 && lastLogIndex < allSessionsWithContext.length - 1) {
                    const nextSessionData = allSessionsWithContext[lastLogIndex + 1];
                    return { program: nextSessionData.program, session: nextSessionData.session, weekVariant: nextSessionData.weekVariant };
                }
            }
        }

        const firstProgram = programs[0];
        if (firstProgram) {
            const firstSession = firstProgram.macrocycles[0]?.blocks?.[0]?.mesocycles[0]?.weeks[0]?.sessions[0];
            if (firstSession) {
                return { program: firstProgram, session: firstSession, weekVariant: firstProgram.macrocycles[0].blocks[0].mesocycles[0].weeks[0].variant };
            }
        }

        return null;
    }, [programs, history]);

    const activeProgram = useMemo(() => nextSessionInfo?.program || programs[0], [nextSessionInfo, programs]);

    return (
        <div className="space-y-6 animate-fade-in">
            <header className="mb-2">
                <h1 className="text-4xl font-bold uppercase tracking-wider">Fuerza</h1>
                <p className="text-slate-400">Tu centro de análisis de fuerza detallado.</p>
            </header>
            
            <CurrentBlockCard />
            <WeeklyWellbeingCard />

            {activeProgram?.carpeDiemEnabled && (
              <CarpeDiemCoachCard program={activeProgram} />
            )}

            <WeeklyProgressAnalysis />
            <WeeklyFatigueCard />
            <InjuryRiskAlerts history={history} />
            <PowerliftingDashboard />
            <IPFPointsCard />
            <EffectiveVolumeCard />
            <RelativeStrengthCard />
            <PersonalRecordsView programs={programs} history={history} settings={settings} />
            <OnThisDayCard />
        </div>
    );
};

export default PowerliftingView;