// services/aiService.ts
import { 
    Settings, Program, WorkoutLog, SkippedWorkoutLog, BodyProgressLog, NutritionLog, 
    Session, ProgramWeek, Exercise, ChatMessage, GenerateContentResponse, MuscleVolumeAnalysis,
    SfrData, ProgramProgressInsight, ExerciseMuscleInfo, FoodItem, WarmupExercise, CoachInsight, PerformanceAnalysis, ImprovementSuggestion,
    BodyLabAnalysis, MuscleGroupAnalysis, BiomechanicalData, BiomechanicalAnalysis, MobilityExercise, PantryItem,
    CarpeDiemPlan, Task, OngoingWorkoutState, AINutritionPlan, AIPantryMealPlan, CompletedSet
} from '../types';
import * as gemini from './geminiService';
import * as deepseek from './deepseekService';
import * as gpt from './gptService';
import { MUSCLE_GROUPS } from '../data/exerciseList';
import { cacheService } from './cacheService';

// --- Type Definitions ---
type AiProviderModule = typeof gemini | typeof deepseek | typeof gpt;
type AiFunction = keyof (typeof gemini & typeof deepseek & typeof gpt);
type ProviderInfo = { name: string; module: AiProviderModule };


// --- Helper Functions ---
function postProcessMuscleActivation(muscles: ExerciseMuscleInfo['involvedMuscles'] | undefined, exerciseName: string): ExerciseMuscleInfo['involvedMuscles'] {
    if (!muscles || muscles.length === 0) {
        return [];
    }
    
    const allActivationsAreZero = muscles.every(m => !m.activation || m.activation === 0);
    
    if (allActivationsAreZero) {
        console.warn(`AI returned all-zero activations for "${exerciseName}". Applying a fallback.`);
        // Create a new array to avoid mutating the original
        const correctedMuscles = JSON.parse(JSON.stringify(muscles));
        
        correctedMuscles.forEach((muscle: any) => {
            switch(muscle.role) {
                case 'primary':
                    muscle.activation = 1.0;
                    break;
                case 'secondary':
                    muscle.activation = 0.6;
                    break;
                case 'stabilizer':
                    muscle.activation = 0.3;
                    break;
                default:
                    muscle.activation = 0.1;
            }
        });

        const hasPrimary = correctedMuscles.some((m: any) => m.role === 'primary');
        if (!hasPrimary && correctedMuscles.length > 0) {
            // Find the one with the highest (but still 0) activation or just the first, and make it primary
            correctedMuscles[0].role = 'primary';
            correctedMuscles[0].activation = 1.0;
        }
        return correctedMuscles;
    }
    
    return muscles;
}

const getProviders = (settings: Settings, functionName: AiFunction): ProviderInfo[] => {
    const providerMap: { [key: string]: AiProviderModule } = { gemini, deepseek, gpt };

    // Functions that REQUIRE Gemini's specific capabilities (vision, search)
    // DeepSeek cannot handle these, so we must exclude it from fallback for these.
    const geminiOnlyFunctions: AiFunction[] = [
        'generateImage',
        'generateImages', 
        'editImageWithText',
        'getCommunityHighlights', 
        'searchWebForExerciseImages', 
        'searchWebForExerciseVideos', 
        'analyzeExerciseVideo',
        'analyzeMealPhoto',
        'searchGoogleImages',
        'analyzePosturePhoto',
        'generateSpeech',
        'analyzeNutritionPlanDocument',
        'analyzeProgressPhoto',
    ];

    if (geminiOnlyFunctions.includes(functionName)) {
        return [{ name: 'gemini', module: gemini }];
    }
    
    const primaryProviderName = settings.apiProvider || 'gemini';
    const providers: ProviderInfo[] = [];
    
    let primaryName = primaryProviderName;
    let fallback1Name: string = 'gemini';
    let fallback2Name: string = 'deepseek';

    if (primaryProviderName === 'gemini') { fallback1Name = 'gpt'; fallback2Name = 'deepseek'; }
    else if (primaryProviderName === 'gpt') { fallback1Name = 'deepseek'; fallback2Name = 'gemini'; }
    else if (primaryProviderName === 'deepseek') { fallback1Name = 'gpt'; fallback2Name = 'gemini'; }

    providers.push({ name: primaryName, module: providerMap[primaryName] });
    
    if (settings.fallbackEnabled) {
        if (fallback1Name !== primaryName) providers.push({ name: fallback1Name, module: providerMap[fallback1Name] });
        if (fallback2Name !== primaryName && fallback2Name !== fallback1Name) providers.push({ name: fallback2Name, module: providerMap[fallback2Name] });
    }
    
    // Return unique providers
    const uniqueProviderNames = new Set<string>();
    return providers.filter(p => {
        if (uniqueProviderNames.has(p.name)) {
            return false;
        }
        uniqueProviderNames.add(p.name);
        return true;
    });
};


const safeJsonParse = <T,>(data: string | object, fallback: T): T => {
    if (typeof data === 'object' && data !== null) {
        return data as T;
    }
    if (typeof data !== 'string') {
        console.error("AI Service: safeJsonParse received non-string, non-object data:", data);
        return fallback;
    }
    try {
        const cleanedJsonString = data.replace(/```json\n?|```/g, '').trim();
        return JSON.parse(cleanedJsonString) as T;
    } catch (error) {
        console.error("AI Service: Failed to parse JSON:", error, "Raw string:", data);
        return fallback;
    }
};

// --- Execution Wrappers ---
const performAICall = async (
    functionName: AiFunction,
    args: any[],
    settings: Settings
): Promise<any> => {
    const providers = getProviders(settings, functionName);
    let lastError: any = null;

    for (const { name, module: providerModule } of providers) {
        if (typeof (providerModule as any)[functionName] === 'function') {
            try {
                // Correctly pass arguments and settings to the provider function
                const response = await ((providerModule as any)[functionName] as Function)(...args, settings);
                return response;
            } catch (error) {
                console.warn(`Provider '${name}' failed for ${String(functionName)}. Trying next. Error:`, error);
                lastError = error;
            }
        }
    }
    throw lastError || new Error(`No provider could execute ${String(functionName)}`);
};


const performAIStreamCall = async function*(
    functionName: AiFunction,
    args: any[],
    settings: Settings
): AsyncGenerator<GenerateContentResponse> {
    const providers = getProviders(settings, functionName);
    let lastError: any = null;
    
    for (const { name, module: providerModule } of providers) {
         if (typeof (providerModule as any)[functionName] === 'function') {
            try {
                // Correctly pass arguments and settings
                const stream = ((providerModule as any)[functionName] as Function)(...args, settings);
                for await (const chunk of stream) {
                    yield chunk;
                }
                return; // End the generator successfully
            } catch (error) {
                console.warn(`Provider '${name}' failed for streaming ${String(functionName)}. Trying next. Error:`, error);
                lastError = error;
            }
        }
    }
    throw lastError || new Error(`No provider could execute streaming ${String(functionName)}`);
}


// --- Exported AI Functions ---
export const generateExerciseClassification = (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => {
    return performAICall('generateExerciseClassification', [exerciseName], settings);
};

export const batchGenerateExercises = async (category: string, muscleGroup: string, count: number, existingExerciseNames: string[], settings: Settings): Promise<ExerciseMuscleInfo[]> => {
    const systemInstruction = `Eres un kinesiólogo experto y un científico de datos de ejercicio. Tu tarea es generar ${count} ejercicios únicos para la categoría '${category}' y el grupo muscular '${muscleGroup}'.
REGLAS:
1. NO generes ninguno de estos nombres (ya existen): ${JSON.stringify(existingExerciseNames)}.
2. Para CADA ejercicio, debes devolver un objeto JSON que cumpla PERFECTAMENTE con esta interfaz de Typescript:
interface ExerciseMuscleInfo {
  id: string; // Genera un UUID, ej: "ai_bicep_curl_01"
  name: string;
  alias?: string;
  description: string;
  involvedMuscles: {
      muscle: string; // Debe ser un valor de la lista de músculos estándar.
      activation: number; // 0.0 a 1.0
      role: 'primary' | 'secondary' | 'stabilizer';
  }[];
  subMuscleGroup?: string;
  category: 'Fuerza' | 'Hipertrofia' | 'Resistencia' | 'Potencia' | 'Movilidad' | 'Pliometría';
  type: 'Básico' | 'Accesorio' | 'Aislamiento';
  equipment: 'Barra' | 'Mancuerna' | 'Máquina' | 'Peso Corporal' | 'Banda' | 'Kettlebell' | 'Polea' | 'Otro';
  force: 'Empuje' | 'Tirón' | 'Bisagra' | 'Sentadilla' | 'Rotación' | 'Anti-Rotación' | 'Otro';
  isCustom?: boolean; // Siempre true
  bodyPart?: 'upper' | 'lower' | 'full';
  chain?: 'anterior' | 'posterior' | 'full';
  isFavorite?: boolean; // Siempre false
  variantOf?: string; // ID de otro ejercicio si es una variante
  sfr?: { score: number; justification: string; };
  setupTime?: number; // 1-10
  technicalDifficulty?: number; // 1-10
  injuryRisk?: { level: number; details: string; }; // 1-10
  transferability?: number; // 1-10
  recommendedMobility?: string[];
  isHallOfFame?: boolean; // Siempre false
  sportsRelevance?: string[];
  baseIFI?: number; // 1.0 a 5.0
  resistanceProfile?: 'stretched' | 'mid-range' | 'contracted' | 'variable';
  commonFaults?: { fault: string; correction: string; }[];
  progressions?: { name: string; description: string; }[];
  regressions?: { name: string; description: string; }[];
  anatomicalConsiderations?: { trait: string; advice: string; }[];
  periodizationNotes?: { phase: string; suitability: number; notes: string; }[];
}
3. Rellena CADA campo con datos científicamente precisos y realistas en español. La propiedad 'id' debe ser un string único.
4. Devuelve SOLAMENTE un array de objetos JSON dentro de una clave 'exercises'. No incluyas nada más en tu respuesta.`;
    
    const prompt = `Genera ${count} ejercicios para la categoría '${category}' y el grupo muscular '${muscleGroup}'.`;
    
    const response = await performAICall('generateContent', [prompt, systemInstruction, { type: "json_object" }], settings);
    
    const parsed = safeJsonParse<any>(response.text, { exercises: [] });
    const exercises = parsed.exercises || [];
    
    if (!Array.isArray(exercises) || (exercises.length > 0 && !exercises.every(e => e.name && e.id))) {
        throw new Error("La IA devolvió una respuesta con formato inválido.");
    }

    return exercises as ExerciseMuscleInfo[];
};

export const analyzeExerciseMuscles = async (exerciseName: string, settings: Settings): Promise<{ involvedMuscles: ExerciseMuscleInfo['involvedMuscles'] }> => {
    const cacheKey = `muscles_${exerciseName.toLowerCase().replace(/\s/g, '_')}`;
    const cached = await cacheService.get<{ involvedMuscles: ExerciseMuscleInfo['involvedMuscles'] }>(cacheKey);
    if (cached) return cached;

    const result = await performAICall('analyzeExerciseMuscles', [exerciseName], settings);
    result.involvedMuscles = postProcessMuscleActivation(result.involvedMuscles, exerciseName);

    await cacheService.set(cacheKey, result);
    return result;
};


export const analyzeProgressPhoto = (base64Image: string, settings: Settings): Promise<string> => {
    return performAICall('analyzeProgressPhoto', [base64Image], settings);
};

export const generateWeeklyProgressAnalysis = (bodyLogs: BodyProgressLog[], nutritionLogs: NutritionLog[], workoutLogs: WorkoutLog[], settings: Settings): Promise<{ summary: string; positiveInsights: string[]; recommendations: string[] }> => {
    return performAICall('generateWeeklyProgressAnalysis', [bodyLogs, nutritionLogs, workoutLogs, settings], settings);
};

export const generateCorrelationAnalysis = (weeklyData: any[], settings: Settings): Promise<{ analysis: string }> => {
    return performAICall('generateCorrelationAnalysis', [weeklyData], settings);
};

export const generateCalibratedSession = (amrapResult: CompletedSet, originalSession: Session, readinessReport: any | null, settings: Settings): Promise<Session> => {
    return performAICall('generateCalibratedSession', [amrapResult, originalSession, readinessReport], settings);
};

export const generatePerformanceAnalysis = (history: WorkoutLog[], skippedLogs: SkippedWorkoutLog[], settings: Settings): Promise<PerformanceAnalysis> => {
    return performAICall('generatePerformanceAnalysis', [history, skippedLogs, settings], settings);
};

export const generateWeekFromPrompt = (program: Program, prompt: string, settings: Settings): Promise<Omit<ProgramWeek, 'id'>> => {
    return performAICall('generateWeekFromPrompt', [program, prompt, settings], settings);
};

export const nudgeToProMode = (): string => {
  return "He notado que has completado 10 sesiones. ¡Gran consistencia! Para llevar tu análisis al siguiente nivel, te recomiendo activar el 'Modo Pro' en Ajustes y empezar a registrar tu RPE.";
};

export const analyzeNutritionPlanDocument = (base64Data: string, mimeType: string, settings: Settings): Promise<{ meals: AINutritionPlan['meals'] }> => {
    return performAICall('analyzeNutritionPlanDocument', [base64Data, mimeType], settings);
};

export const generateTasksFromWorkout = (log: WorkoutLog, history: WorkoutLog[], settings: Settings): Promise<{ tasks: Omit<Task, 'id' | 'completed'>[] }> => {
    return performAICall('generateTasksFromWorkout', [log, history], settings);
};

export const createAndPopulateExercise = async (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => {
    try {
        const result = await performAICall('createAndPopulateExercise', [exerciseName], settings);
        result.involvedMuscles = postProcessMuscleActivation(result.involvedMuscles, exerciseName);
        return result;
    } catch (error) {
        console.error(`Failed to populate all exercise data for ${exerciseName}:`, error);
        // Fallback to simpler analysis if the complex one fails
        try {
            const muscleData = await analyzeExerciseMuscles(exerciseName, settings);
            return {
                name: exerciseName,
                description: '',
                involvedMuscles: muscleData.involvedMuscles
            };
        } catch (innerError) {
             return {
                name: exerciseName,
                description: '',
                involvedMuscles: [],
            };
        }
    }
};

export const createAndPopulateFoodItem = async (foodName: string, settings: Settings): Promise<Partial<FoodItem>> => {
    // Wrap to ensure micronutrients are requested in the prompt
    return performAICall('createAndPopulateFoodItem', [foodName], settings);
};

export const generateCarpeDiemWeeklyPlan = (program: Program, history: WorkoutLog[], settings: Settings, calorieGoal: 'deficit' | 'maintenance' | 'surplus'): Promise<CarpeDiemPlan> => {
    return performAICall('generateCarpeDiemWeeklyPlan', [program, history, calorieGoal], settings);
};

export const generateExerciseProgressReport = (exerciseName: string, exerciseLogs: WorkoutLog[], settings: Settings): Promise<{ summary: string; positives: string[]; areasForImprovement: string[] }> => {
    return performAICall('generateExerciseProgressReport', [exerciseName, exerciseLogs], settings);
};

export const getNutritionalInfoForPantryItem = (itemName: string, settings: Settings): Promise<{ name: string; calories: number; protein: number; carbs: number; fats: number; }> => {
    return performAICall('getNutritionalInfoForPantryItem', [itemName], settings);
};

export const generateMealSuggestion = (pantryItems: PantryItem[], remainingMacros: any, settings: Settings): Promise<AIPantryMealPlan> => {
    return performAICall('generateMealSuggestion', [pantryItems, remainingMacros], settings);
};

export const generateMealsFromPantry = (pantryItems: PantryItem[], settings: Settings): Promise<AIPantryMealPlan> => {
    return performAICall('generateMealsFromPantry', [pantryItems], settings);
};

export const generateWeightProjection = (
    avgIntake: number,
    tdee: number,
    weightHistory: { date: string, weight?: number }[],
    targetWeight: number,
    settings: Settings
): Promise<{ projection: string; summary: string }> => {
    return performAICall('generateWeightProjection', [avgIntake, tdee, weightHistory, targetWeight], settings);
};

export const generateExerciseCollection = async (purpose: string, settings: Settings): Promise<{ collectionName: string; exercises: { name: string; justification: string; }[] }> => {
    const result = await performAICall('generateExercisesForPurpose', [purpose], settings);
    return {
        collectionName: purpose,
        exercises: result.exercises.map((ex: any) => ({ name: ex.name, justification: ex.justification }))
    };
};

export const generateExercisesForPurpose = (purpose: string, settings: Settings): Promise<{ exercises: { name: string; justification: string; primaryMuscles: string[] }[] }> => {
    return performAICall('generateExercisesForPurpose', [purpose], settings);
};

export const generateBodyLabAnalysis = (programs: Program[], history: WorkoutLog[], settings: Settings): Promise<BodyLabAnalysis> => {
    return performAICall('generateBodyLabAnalysis', [programs, history, settings], settings);
};

export const generateBiomechanicalAnalysis = (data: BiomechanicalData, exercises: string[], settings: Settings): Promise<BiomechanicalAnalysis> => {
    return performAICall('generateBiomechanicalAnalysis', [data, exercises], settings);
};

export const getAICoachInsights = (history: WorkoutLog[], programs: Program[], settings: Settings, bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[]): Promise<CoachInsight> => {
    return performAICall('getAICoachInsights', [history, programs, settings, bodyProgress, nutritionLogs], settings);
};

export const generateImage = (prompt: string, aspectRatio: string, settings: Settings): Promise<string> => {
    return performAICall('generateImage', [prompt, aspectRatio], settings);
};

export const generateImages = (prompt: string, aspectRatio: string, settings: Settings): Promise<{ imageUrls: string[] }> => {
    return performAICall('generateImages', [prompt, aspectRatio], settings);
};

export const getCoachChatResponseStream = (prompt: string, messages: ChatMessage[], programs: Program[], history: WorkoutLog[], settings: Settings, sessionContext?: Session | OngoingWorkoutState) => performAIStreamCall('getCoachChatResponseStream', [prompt, messages, programs, history, settings, sessionContext], settings);

export const getPhysicalProgressChatResponseStream = (prompt: string, messages: ChatMessage[], bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[], settings: Settings) => performAIStreamCall('getPhysicalProgressChatResponseStream', [prompt, messages, bodyProgress, nutritionLogs, settings], settings);

export const getNutritionalInfo = (description: string, settings: Settings): Promise<Omit<FoodItem, 'id'>> => {
    return performAICall('getNutritionalInfo', [description], settings);
};

export const analyzeMealPhoto = (base64Image: string, settings: Settings): Promise<Omit<FoodItem, 'id'>> => {
    return performAICall('analyzeMealPhoto', [base64Image], settings);
};

export const analyzeExerciseVideo = (base64Video: string, exerciseName: string, settings: Settings): Promise<{ text: string }> => {
    return performAICall('analyzeExerciseVideo', [base64Video, exerciseName], settings);
};

export const generateTimeSaverSuggestions = (remainingExercises: Exercise[], timeAvailable: number, settings: Settings): Promise<{ suggestions: any[] }> => {
    return performAICall('generateTimeSaverSuggestions', [remainingExercises, timeAvailable], settings);
};

export const generateExerciseDescription = (exerciseName: string, settings: Settings): Promise<string> => {
    return performAICall('generateExerciseDescription', [exerciseName], settings);
};

export const generateExerciseAlias = (exerciseName: string, settings: Settings): Promise<{ alias: string }> => {
    return performAICall('generateExerciseAlias', [exerciseName], settings);
};

export const generateKinesiologyAnalysis = (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => {
    return performAICall('generateKinesiologyAnalysis', [exerciseName], settings);
};

export const getPrimeStarsRating = (exerciseName: string, settings: Settings): Promise<{ score: number; justification: string; }> => {
    return performAICall('getPrimeStarsRating', [exerciseName], settings);
};

export const getCommunityOpinionForExercise = (exerciseName: string, settings: Settings): Promise<string[]> => {
    return performAICall('getCommunityOpinionForExercise', [exerciseName], settings);
};

export const searchGoogleImages = (query: string, settings: Settings): Promise<{ imageUrls: string[] }> => {
    return performAICall('searchGoogleImages', [query], settings);
};

export const searchWebForExerciseVideos = (exerciseName: string, settings: Settings): Promise<{ videoUrls: string[] }> => {
    return performAICall('searchWebForExerciseVideos', [exerciseName], settings);
};

export const estimateSFR = (exerciseName: string, settings: Settings): Promise<{ score: number, justification: string }> => {
    return performAICall('estimateSFR', [exerciseName], settings);
};

export const generateWorkoutPostSummary = (log: WorkoutLog, previousLogs: WorkoutLog[], settings: Settings): Promise<{ title: string; summary: string }> => {
    return performAICall('generateWorkoutPostSummary', [log, previousLogs], settings);
};

export const getCommunityHighlights = (settings: Settings): Promise<{ highlights: any[] }> => {
    return performAICall('getCommunityHighlights', [settings], settings);
};

export const generateImprovementSuggestions = (history: WorkoutLog[], programs: Program[], settings: Settings): Promise<ImprovementSuggestion[]> => {
    return performAICall('generateImprovementSuggestions', [history, programs, settings], settings);
};

export const generateMuscleGroupAnalysis = (muscleName: string, trainingData: any, settings: Settings): Promise<MuscleGroupAnalysis> => {
    return performAICall('generateMuscleGroupAnalysis', [muscleName, trainingData], settings);
};

export const generateMobilityRoutine = (target: string, settings: Settings): Promise<MobilityExercise[]> => {
    return performAICall('generateMobilityRoutine', [target], settings);
};

export const generateFoodCategoryDescription = (categoryName: string, settings: Settings): Promise<string> => {
    return performAICall('generateFoodCategoryDescription', [categoryName], settings);
};

export const editImageWithText = (base64Image: string, prompt: string, settings: Settings): Promise<string> => {
    return performAICall('editImageWithText', [base64Image, prompt], settings);
};

export const analyzePosturePhoto = (base64Image: string, settings: Settings): Promise<string> => {
    return performAICall('analyzePosturePhoto', [base64Image], settings);
};

export const generateSpeech = (text: string, settings: Settings): Promise<string | null> => {
    return performAICall('generateSpeech', [text], settings);
};

export const searchWebForExerciseImages = (exerciseName: string, settings: Settings): Promise<{ imageUrls: string[] }> => {
    return performAICall('searchWebForExerciseImages', [exerciseName], settings);
};

export const getAIGlobalRating = (exerciseName: string, settings: Settings): Promise<{ score: number }> => {
    return performAICall('getAIGlobalRating', [exerciseName], settings);
};

export const generateSessionScore = (log: WorkoutLog, previousLogs: WorkoutLog[], settings: Settings): Promise<{ score: number }> => {
    return performAICall('generateSessionScore', [log, previousLogs], settings);
};

export const generateOnThisDayMessage = (exerciseName: string, oldSet: { weight: number; reps: number }, newSet: { weight: number; reps: number }, settings: Settings): Promise<{ message: string }> => {
    return performAICall('generateOnThisDayMessage', [exerciseName, oldSet, newSet], settings);
};

export const suggestExerciseAlternatives = (exercise: Exercise, reason: string, primaryMuscle: string, settings: Settings): Promise<{ alternatives: { name: string; justification: string }[] }> => {
    return performAICall('suggestExerciseAlternatives', [exercise, reason, primaryMuscle], settings);
};

export const getAICoachAnalysis = async (exerciseName: string, settings: Settings): Promise<{ summary: string; pros: string[]; cons: string[] }> => {
    const cacheKey = `coach_analysis_${exerciseName.toLowerCase().replace(/\s/g, '_')}`;
    const cached = await cacheService.get<{ summary: string; pros: string[]; cons: string[] }>(cacheKey);
    if (cached) return cached;
    const result = await performAICall('getAICoachAnalysis', [exerciseName], settings);
    await cacheService.set(cacheKey, result);
    return result;
};