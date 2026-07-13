"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.WorkoutLogSchema = exports.PORTION_MULTIPLIERS = void 0;
// types.ts
const zod_1 = require("zod");
exports.PORTION_MULTIPLIERS = {
    small: 0.6,
    medium: 1,
    large: 1.5,
    extra: 2,
};
// Zod Schema for WorkoutLog validation
exports.WorkoutLogSchema = zod_1.z.object({
    id: zod_1.z.string(),
    programId: zod_1.z.string(),
    programName: zod_1.z.string(),
    sessionId: zod_1.z.string(),
    sessionName: zod_1.z.string(),
    date: zod_1.z.string(),
    duration: zod_1.z.number().optional(),
    completedExercises: zod_1.z.array(zod_1.z.any()), // Simplified for now
    notes: zod_1.z.string().optional(),
    discomforts: zod_1.z.array(zod_1.z.string()).optional(),
    fatigueLevel: zod_1.z.number(),
    mentalClarity: zod_1.z.number(),
    gymName: zod_1.z.string().optional(),
    photoUri: zod_1.z.string().optional(),
    sessionVariant: zod_1.z.enum(['A', 'B', 'C', 'D']).optional(),
    planDeviations: zod_1.z.array(zod_1.z.any()).optional(),
    readiness: zod_1.z.any().optional(),
    focus: zod_1.z.number().optional(),
    pump: zod_1.z.number().optional(),
    environmentTags: zod_1.z.array(zod_1.z.string()).optional(),
    sessionDifficulty: zod_1.z.number().optional(),
    planAdherenceTags: zod_1.z.array(zod_1.z.string()).optional(),
    sessionStressScore: zod_1.z.number().optional(),
    muscleBatteries: zod_1.z.record(zod_1.z.string(), zod_1.z.number()).optional(),
    postTitle: zod_1.z.string().optional(),
    postSummary: zod_1.z.string().optional(),
    postPhotos: zod_1.z.array(zod_1.z.string()).optional(),
    isCustomPost: zod_1.z.boolean().optional(),
    photo: zod_1.z.string().optional(),
    caloriesBurned: zod_1.z.number().optional(),
});
