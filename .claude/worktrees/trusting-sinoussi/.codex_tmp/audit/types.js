// types.ts
import { z } from 'zod';
export const PORTION_MULTIPLIERS = {
    small: 0.6,
    medium: 1,
    large: 1.5,
    extra: 2,
};
// Zod Schema for WorkoutLog validation
export const WorkoutLogSchema = z.object({
    id: z.string(),
    programId: z.string(),
    programName: z.string(),
    sessionId: z.string(),
    sessionName: z.string(),
    date: z.string(),
    duration: z.number().optional(),
    completedExercises: z.array(z.any()), // Simplified for now
    notes: z.string().optional(),
    discomforts: z.array(z.string()).optional(),
    fatigueLevel: z.number(),
    mentalClarity: z.number(),
    gymName: z.string().optional(),
    photoUri: z.string().optional(),
    sessionVariant: z.enum(['A', 'B', 'C', 'D']).optional(),
    planDeviations: z.array(z.any()).optional(),
    readiness: z.any().optional(),
    focus: z.number().optional(),
    pump: z.number().optional(),
    environmentTags: z.array(z.string()).optional(),
    sessionDifficulty: z.number().optional(),
    planAdherenceTags: z.array(z.string()).optional(),
    sessionStressScore: z.number().optional(),
    muscleBatteries: z.record(z.string(), z.number()).optional(),
    postTitle: z.string().optional(),
    postSummary: z.string().optional(),
    postPhotos: z.array(z.string()).optional(),
    isCustomPost: z.boolean().optional(),
    photo: z.string().optional(),
    caloriesBurned: z.number().optional(),
});
