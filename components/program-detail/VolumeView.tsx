import React from 'react';
import { Program, ProgramWeek, Settings } from '../../types';
import AnalyticsDashboard from './AnalyticsDashboard';

interface VolumeViewProps {
    program: Program;
    history: any[];
    settings: Settings | any;
    isOnline: boolean;
    isActive: boolean;
    currentWeeks: (ProgramWeek & { mesoIndex: number })[];
    selectedWeekId?: string;
    onSelectWeek: (id: string) => void;
    visualizerData: any[];
    displayedSessions: any[];
    totalAdherence: number;
    weeklyAdherence: { weekName: string; pct: number }[];
    programDiscomforts: { name: string; count: number }[];
    adaptiveCache: any;
    exerciseList: any[];
    setSettings?: (partial: Partial<Settings>) => void;
    onUpdateProgram?: (p: Program) => void;
    addToast?: any;
    postSessionFeedback?: any;
}

const VolumeView: React.FC<VolumeViewProps> = (props) => {
    return (
        <div className="px-0 py-0">
            <AnalyticsDashboard
                program={props.program}
                history={props.history}
                settings={props.settings}
                isOnline={props.isOnline}
                isActive={props.isActive}
                currentWeeks={props.currentWeeks}
                selectedWeekId={props.selectedWeekId || null}
                onSelectWeek={props.onSelectWeek}
                visualizerData={props.visualizerData}
                displayedSessions={props.displayedSessions}
                totalAdherence={props.totalAdherence}
                weeklyAdherence={props.weeklyAdherence}
                programDiscomforts={props.programDiscomforts}
                adaptiveCache={props.adaptiveCache}
                exerciseList={props.exerciseList}
                setSettings={props.setSettings}
                onUpdateProgram={props.onUpdateProgram}
                addToast={props.addToast}
                postSessionFeedback={props.postSessionFeedback}
            />
        </div>
    );
};

export default VolumeView;
