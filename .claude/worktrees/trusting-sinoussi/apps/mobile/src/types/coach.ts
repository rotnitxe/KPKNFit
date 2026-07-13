export type CoachMessageRole = 'user' | 'assistant' | 'system';

export interface CoachChatMessage {
  id: string;
  role: CoachMessageRole;
  text: string;
  createdAt: string;
}

export interface CoachConversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: CoachChatMessage[];
}

export interface CoachContextSnapshot {
  activeProgramName: string | null;
  weeklySessionCount: number;
  completedSetsThisWeek: number;
  plannedSetsThisWeek: number;
  latestWeight: number | null;
  latestBodyFat: number | null;
  todayCalories: number;
  todayProtein: number;
  readiness: number | null;
}
