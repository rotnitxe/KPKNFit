// services/deepseekService.ts
import { 
    GenerateContentResponse, ChatMessage, Session, WarmupExercise, MuscleVolumeAnalysis, 
    SfrData, ProgramProgressInsight, CoachInsight, PerformanceAnalysis, ProgramWeek,
    NutritionLog, BodyProgressLog, FoodItem, ExerciseMuscleInfo, Exercise, WorkoutLog, SkippedWorkoutLog, Program, Settings,
    ImprovementSuggestion, BodyLabAnalysis, MuscleGroupAnalysis, BiomechanicalData, BiomechanicalAnalysis, MobilityExercise, PantryItem, Task,
    CarpeDiemPlan, AIPantryMealPlan, AINutritionPlan, CompletedSet
} from '../types';

const API_URL = "https://api.deepseek.com/chat/completions";

const safeJsonParse = <T,>(data: string | object, fallback: T): T => {
    if (typeof data === 'object' && data !== null) {
        return data as T;
    }
    if (typeof data !== 'string') {
        console.error("AI Service: safeJsonParse received non-string, non-object data:", data);
        return fallback;
    }
    try {
        const cleanedJsonString = data.replace(/```json\n|```/g, '').trim();
        return JSON.parse(cleanedJsonString) as T;
    } catch (error) {
        console.error("AI Service: Failed to parse JSON:", error, "Raw string:", data);
        return fallback;
    }
};

export const generateContent = async (
    prompt: string,
    systemInstruction: string | undefined,
    jsonResponseSchema: any,
    settings: Settings
): Promise<GenerateContentResponse> => {
    if (!navigator.onLine) {
        throw new Error("Estás sin conexión. DeepSeek requiere acceso a internet.");
    }
    
    const apiKey = settings.apiKeys?.deepseek;
    if (!apiKey) {
      throw new Error("La clave API de DeepSeek no está configurada en los ajustes.");
    }

    const messages: { role: 'system' | 'user'; content: string }[] = [];
    if (systemInstruction) {
        messages.push({ role: 'system', content: systemInstruction });
    }
    messages.push({ role: 'user', content: prompt });

    const body: any = {
        model: "deepseek-chat",
        messages,
    };

    if (jsonResponseSchema) {
        body.response_format = { type: "json_object" };
    }

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`,
            },
            body: JSON.stringify(body),
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(`DeepSeek API Error: ${response.status} ${response.statusText} - ${errorData.error?.message}`);
        }

        const data = await response.json();
        const text = data.choices[0]?.message?.content || '';
        
        return { text };

    } catch (error) {
        console.error("Error en la solicitud a DeepSeek:", error);
        throw new Error("La solicitud a la IA de DeepSeek falló.");
    }
};

export const generateContentStream = async function* (
    messages: ChatMessage[],
    systemInstruction: string | undefined,
    settings: Settings
): AsyncGenerator<GenerateContentResponse> {
    const lastMessage = messages[messages.length - 1].parts[0].text;
    const response = await generateContent(lastMessage, systemInstruction, undefined, settings);
    yield { text: response.text };
};

export const generateSession = async (prompt: string, settings: Settings): Promise<Omit<Session, 'id'>> => {
    const systemInstruction = `Eres un experto entrenador de fitness. Genera una sesión de entrenamiento basada en la petición del usuario. Responde ÚNICAMENTE con un objeto JSON en español que siga esta estructura: { "name": "...", "description": "...", "exercises": [{ "name": "...", "trainingMode": "reps" | "time", "restTime": 90, "sets": [{ "targetReps": 10, "targetDuration": 60, "targetRPE": 8 }] }] }. Para sets de repeticiones, usa 'targetReps'. Para sets de tiempo, usa 'targetDuration' en segundos. Asegúrate de que las repeticiones estén entre 1-30, la duración entre 10-300 segundos, y el RPE entre 5-10. Todos los valores de texto deben estar en español.`;
    const response = await generateContent(prompt, systemInstruction, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { name: 'Error', description: 'Fallo al analizar', exercises: [] });
};

export const generateWarmupForSession = async (session: Session, settings: Settings): Promise<Omit<WarmupExercise, 'id'>[]> => {
    const prompt = `Crea una rutina de calentamiento dinámico para esta sesión de entrenamiento: ${JSON.stringify(session.exercises.map(e => e.name))}. Enfócate en la movilidad y activación de los músculos involucrados. Proporciona 3-5 pasos. Responde ÚNICAMENTE con un array JSON de objetos con las claves "name", "sets", y "reps" (ej., "10" o "30s"), todo en español.`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, []);
};

export const analyzeWorkoutVolume = async (volumeData: Omit<MuscleVolumeAnalysis, 'assessment'>[], settings: Settings): Promise<MuscleVolumeAnalysis[]> => {
    const prompt = `Analiza estos datos de volumen de entrenamiento semanal. Para cada grupo muscular, proporciona una 'assessment' (evaluación) basada en principios establecidos (ej., MEV, MAV, MRV). Responde ÚNICAMENTE con un array JSON de objetos, añadiendo la clave 'assessment' a cada objeto. Los valores deben estar en español. Datos: ${JSON.stringify(volumeData)}`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    const result = safeJsonParse<any>(response.text, null);
    // Deepseek might return the data inside a key, so we need to find it.
    if (result && Array.isArray(result)) return result;
    if (result && typeof result === 'object' && Array.isArray(Object.values(result)[0])) return Object.values(result)[0] as MuscleVolumeAnalysis[];
    return volumeData.map(v => ({...v, assessment: 'N/A'}));
};

export const estimateSFR = async (exerciseName: string, settings: Settings): Promise<{ score: number, justification: string }> => {
    const prompt = `Estima el Ratio Estímulo-Fatiga (SFR) para el ejercicio "${exerciseName}" en una escala de 1-10 (10 es el mejor). Proporciona una breve justificación en español. Responde ÚNICAMENTE con un objeto JSON: { "score": number, "justification": "string" }.`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { score: 5, justification: 'No se pudo analizar.' });
};

export const generateProgramProgressInsights = async (program: Program, history: WorkoutLog[], volumeDataWithAssessment: MuscleVolumeAnalysis[], sfrData: SfrData[], settings: Settings): Promise<ProgramProgressInsight> => {
    const prompt = `Analiza el progreso de mi programa de entrenamiento. Te proporcionaré la estructura del programa, historial, análisis de volumen y datos de SFR. Dame un resumen, correlaciones positivas y áreas de mejora. Responde ÚNICAMENTE con JSON en español. Datos: ${JSON.stringify({ program, history: history.slice(-10), volume: volumeDataWithAssessment, sfr: sfrData })}`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { summary: 'Error', positiveCorrelations: [], improvementAreas: [] });
};

export const getAICoachInsights = async (history: WorkoutLog[], programs: Program[], settings: Settings, bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[]): Promise<CoachInsight> => {
    const prompt = `Analiza las últimas 4 semanas de datos de entrenamiento. Identifica tendencias, posibles estancamientos o riesgos. Proporciona un hallazgo principal, 2-3 sugerencias accionables y un alertLevel ('info', 'warning', 'danger'). Responde ÚNICAMENTE con JSON en español. Datos: ${JSON.stringify({ history: history.slice(-20) })}`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { title: 'Error', findings: 'No se pudo analizar.', suggestions: [], alertLevel: 'danger' });
};

export const getNutritionalInfo = async (description: string, settings: Settings): Promise<Omit<FoodItem, 'id'>> => {
    const prompt = `Estima la información nutricional para: "${description}". Responde ÚNICAMENTE con un objeto JSON: { "name": "string", "calories": number, "protein": number, "carbs": number, "fats": number }, todo en español.`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    const parsed = safeJsonParse<Partial<Omit<FoodItem, 'id'>>>(response.text, {});
    return {
        name: parsed.name || 'Análisis fallido',
        calories: parsed.calories || 0,
        protein: parsed.protein || 0,
        carbs: parsed.carbs || 0,
        fats: parsed.fats || 0,
        servingSize: 1,
        unit: 'unit'
    };
};

export const analyzeWeeklyProgress = async (thisWeek: WorkoutLog[], lastWeek: WorkoutLog[], skipped: SkippedWorkoutLog[], settings: Settings): Promise<{ percentageChange: number; summary: string }> => {
    const prompt = `Compara el volumen de entrenamiento de esta semana con la anterior. Calcula el cambio porcentual en el volumen total (peso * reps * series). Proporciona un resumen corto y motivacional del rendimiento de la semana. Responde ÚNICAMENTE con JSON en español: { "percentageChange": number, "summary": "string" }. Datos: ${JSON.stringify({ thisWeek, lastWeek, skipped })}`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { percentageChange: 0, summary: 'Análisis fallido.' });
};

export const generatePerformanceAnalysis = async (history: WorkoutLog[], skippedLogs: SkippedWorkoutLog[], settings: Settings): Promise<PerformanceAnalysis> => {
    const prompt = `Analiza los últimos 7 días de datos de entrenamiento. Proporciona una puntuación de rendimiento (1-7), un resumen breve, 2 puntos positivos y 2 puntos negativos/de mejora. Responde ÚNICAMENTE con JSON en español: { "score": number, "summary": "string", "positivePoints": ["string"], "negativePoints": ["string"] }. Datos: ${JSON.stringify({ workouts: history.slice(-10), skipped: skippedLogs })}`;
    const response = await generateContent(prompt, undefined, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { score: 0, summary: 'Error', positivePoints: [], negativePoints: [] });
};

export const generateWeekFromPrompt = async (program: Program, prompt: string, settings: Settings): Promise<Omit<ProgramWeek, 'id'>> => {
    const context = `Contexto del programa actual: ${program.name}. Sesiones de la última semana: ${JSON.stringify(program.macrocycles.slice(-1)[0].blocks?.slice(-1)[0]?.mesocycles.slice(-1)[0].weeks.slice(-1)[0]?.sessions.map(s => s.name) || 'N/A')}`;
    const systemInstruction = `Eres un experto entrenador de fitness. Crea una nueva semana de entrenamientos basada en el objetivo del usuario, usando el contexto proporcionado para aplicar sobrecarga progresiva. Responde ÚNICAMENTE con un objeto JSON en español: { "name": "Nombre de la Semana", "sessions": [{ "name": "Nombre de la Sesión", "description": "...", "exercises": [...] }] }. La estructura de los ejercicios debe ser la misma que en generateSession.`;
    const fullPrompt = `${context}\n\nObjetivo del Usuario: ${prompt}`;
    const response = await generateContent(fullPrompt, systemInstruction, { type: "json_object" }, settings);
    return safeJsonParse(response.text, { name: 'Error', sessions: [] });
};


// --- Not Implemented Functions ---

const throwNotImplemented = (funcName: string) => {
    throw new Error(`La función '${funcName}' no está implementada para el proveedor DeepSeek.`);
};

export const generateExerciseClassification = async (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => throwNotImplemented('generateExerciseClassification');
export const analyzeExerciseMuscles = async (exerciseName: string, settings: Settings): Promise<{ involvedMuscles: ExerciseMuscleInfo['involvedMuscles'] }> => {
    // This is a stub, returning an empty array to maintain interface consistency.
    console.warn(`DeepSeek provider does not implement analyzeExerciseMuscles for "${exerciseName}". Returning empty.`);
    return { involvedMuscles: [] };
};

export const generateKinesiologyAnalysis = async (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => throwNotImplemented('generateKinesiologyAnalysis');
export const generateCalibratedSession = async (amrapResult: CompletedSet, originalSession: Session, readinessReport: any | null, settings: Settings): Promise<Session> => throwNotImplemented('generateCalibratedSession');
export const generateWeeklyProgressAnalysis = async (bodyLogs: BodyProgressLog[], nutritionLogs: NutritionLog[], workoutLogs: WorkoutLog[], settings: Settings): Promise<{ summary: string; positiveInsights: string[]; recommendations: string[] }> => throwNotImplemented('generateWeeklyProgressAnalysis');
export const analyzeProgressPhoto = async (base64Image: string, settings: Settings): Promise<string> => throwNotImplemented('analyzeProgressPhoto');
export const generateExercisesForPurpose = async (purpose: string, settings: Settings): Promise<any> => throwNotImplemented('generateExercisesForPurpose');
export const generateTasksFromWorkout = async (log: WorkoutLog, history: WorkoutLog[], settings: Settings): Promise<{ tasks: Omit<Task, 'id' | 'completed'>[] }> => throwNotImplemented('generateTasksFromWorkout');
export const getCoachChatResponseStream = async function* (prompt: string, messages: ChatMessage[], programs: Program[], history: WorkoutLog[], settings: Settings): AsyncGenerator<GenerateContentResponse> { yield* generateContentStream(messages, "Eres un entrenador de fitness servicial y alentador. Responde en español.", settings); };
export const getPhysicalProgressChatResponseStream = async function* (prompt: string, messages: ChatMessage[], bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[], settings: Settings): AsyncGenerator<GenerateContentResponse> { yield* generateContentStream(messages, "Eres un entrenador de nutrición y progreso servicial. Responde en español.", settings); };
export const getPeriodizationCoachStream = async function* (prompt: string, messages: ChatMessage[], settings: Settings): AsyncGenerator<GenerateContentResponse> { yield* generateContentStream(messages, "Eres un entrenador experto especializado en periodización del entrenamiento de fuerza. Responde en español.", settings); };
export const generateImage = async (prompt: string, aspectRatio: string, settings: Settings): Promise<string> => throwNotImplemented('generateImage');
export const generateImages = async (prompt: string, aspectRatio: string, settings: Settings): Promise<any> => throwNotImplemented('generateImages');
export const editImageWithText = async (base64Image: string, prompt: string, settings: Settings): Promise<string> => throwNotImplemented('editImageWithText');
export const analyzePosturePhoto = async (base64Image: string, settings: Settings): Promise<string> => throwNotImplemented('analyzePosturePhoto');
export const analyzeMealPhoto = async (base64Image: string, settings: Settings): Promise<Omit<FoodItem, 'id'>> => throwNotImplemented('analyzeMealPhoto');
export const analyzeExerciseVideo = async (base64Video: string, exerciseName: string, settings: Settings): Promise<{ text: string }> => throwNotImplemented('analyzeExerciseVideo');
export const searchGoogleImages = async (query: string, settings: Settings): Promise<{ imageUrls: string[] }> => throwNotImplemented('searchGoogleImages');
export const searchWebForExerciseImages = async (exerciseName: string, settings: Settings): Promise<{ imageUrls: string[] }> => throwNotImplemented('searchWebForExerciseImages');
export const searchWebForExerciseVideos = async (exerciseName: string, settings: Settings): Promise<{ videoUrls: string[] }> => throwNotImplemented('searchWebForExerciseVideos');
export const getCommunityHighlights = async (settings: Settings): Promise<{ highlights: any[] }> => throwNotImplemented('getCommunityHighlights');
export const getCommunityOpinionForExercise = async (exerciseName: string, settings: Settings): Promise<string[]> => throwNotImplemented('getCommunityOpinionForExercise');
export const getAICoachAnalysis = async (exerciseName: string, settings: Settings): Promise<any> => throwNotImplemented('getAICoachAnalysis');
export const getAIGlobalRating = async (exerciseName: string, settings: Settings): Promise<{ score: number }> => throwNotImplemented('getAIGlobalRating');
export const generateSessionScore = async (log: WorkoutLog, previousLogs: WorkoutLog[], settings: Settings): Promise<{ score: number }> => throwNotImplemented('generateSessionScore');
export const generateOnThisDayMessage = async (exerciseName: string, oldSet: { weight: number; reps: number }, newSet: { weight: number; reps: number }, settings: Settings): Promise<{ message: string }> => throwNotImplemented('generateOnThisDayMessage');
export const suggestExerciseAlternatives = async (exercise: Exercise, reason: string, primaryMuscle: string, settings: Settings): Promise<{ alternatives: { name: string; justification: string }[] }> => throwNotImplemented('suggestExerciseAlternatives');
export const generateExerciseAlias = async (exerciseName: string, settings: Settings): Promise<{ alias: string }> => throwNotImplemented('generateExerciseAlias');
export const analyzeWorkoutVolumeWithAI = async (volumeData: Omit<MuscleVolumeAnalysis, 'assessment'>[], settings: Settings): Promise<MuscleVolumeAnalysis[]> => throwNotImplemented('analyzeWorkoutVolumeWithAI');
export const getPrimeStarsRating = async (exerciseName: string, settings: Settings): Promise<{ score: number; justification: string; }> => throwNotImplemented('getPrimeStarsRating');
export const generateTimeSaverSuggestions = async (remainingExercises: Exercise[], timeAvailable: number, settings: Settings): Promise<{ suggestions: any[] }> => throwNotImplemented('generateTimeSaverSuggestions');
export const generateExerciseDescription = async (exerciseName: string, settings: Settings): Promise<string> => throwNotImplemented('generateExerciseDescription');
export const generateWorkoutPostSummary = async (log: WorkoutLog, previousLogs: WorkoutLog[], settings: Settings): Promise<{ title: string; summary: string }> => throwNotImplemented('generateWorkoutPostSummary');
export const generateImprovementSuggestions = async (history: WorkoutLog[], programs: Program[], settings: Settings): Promise<ImprovementSuggestion[]> => throwNotImplemented('generateImprovementSuggestions');
export const generateBodyLabAnalysis = async (programs: Program[], history: WorkoutLog[], settings: Settings): Promise<BodyLabAnalysis> => throwNotImplemented('generateBodyLabAnalysis');
export const generateMuscleGroupAnalysis = async (muscleName: string, trainingData: any, settings: Settings): Promise<MuscleGroupAnalysis> => throwNotImplemented('generateMuscleGroupAnalysis');
export const generateBiomechanicalAnalysis = async (data: BiomechanicalData, exercises: string[], settings: Settings): Promise<BiomechanicalAnalysis> => throwNotImplemented('generateBiomechanicalAnalysis');
export const generateMobilityRoutine = async (target: string, settings: Settings): Promise<MobilityExercise[]> => throwNotImplemented('generateMobilityRoutine');
export const generateWeightProjection = async (avgIntake: number, tdee: number, weightHistory: { date: string, weight?: number }[], targetWeight: number, settings: Settings): Promise<{ projection: string; summary: string }> => throwNotImplemented('generateWeightProjection');
export const getNutritionalInfoForPantryItem = async (itemName: string, settings: Settings): Promise<any> => throwNotImplemented('getNutritionalInfoForPantryItem');
export const generateMealSuggestion = async (pantryItems: PantryItem[], remainingMacros: any, settings: Settings): Promise<any> => throwNotImplemented('generateMealSuggestion');
export const createAndPopulateFoodItem = async (foodName: string, settings: Settings): Promise<Partial<FoodItem>> => throwNotImplemented('createAndPopulateFoodItem');
export const generateMealsFromPantry = async (pantryItems: PantryItem[], settings: Settings): Promise<AIPantryMealPlan> => throwNotImplemented('generateMealsFromPantry');
export const analyzeNutritionPlanDocument = async (base64Data: string, mimeType: string, settings: Settings): Promise<{ meals: AINutritionPlan['meals'] }> => throwNotImplemented('analyzeNutritionPlanDocument');
export const createAndPopulateExercise = async (exerciseName: string, settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => throwNotImplemented('createAndPopulateExercise');
export const generateSpeech = async (text: string, settings: Settings): Promise<string | null> => throwNotImplemented('generateSpeech');