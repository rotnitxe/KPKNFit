
// services/geminiService.ts
import { GoogleGenAI, Content, GenerateContentResponse, Type, Blob, Modality, FunctionDeclaration, LiveServerMessage } from "@google/genai";
import { ChatMessage, Settings, WorkoutLog, Exercise, PlanDeviation, Program, ImprovementSuggestion, BodyLabAnalysis, BiomechanicalAnalysis, BiomechanicalData, MobilityExercise, PantryItem, FoodItem, CarpeDiemPlan, Session, ExerciseMuscleInfo, Task, AINutritionPlan, AIPantryMealPlan, CompletedSet, BodyProgressLog, NutritionLog, CoachInsight, OngoingWorkoutState } from '../types';
import { calculateBrzycki1RM } from "../utils/calculations";
import { MUSCLE_GROUPS } from '../data/exerciseList';

/**
 * Initializes the Gemini AI client using the pre-configured environment variable.
 */
const getClient = (): GoogleGenAI => {
    return new GoogleGenAI({ apiKey: process.env.API_KEY });
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

/**
 * Executes a standard generateContent request.
 */
const makeGenerateContentRequest = async (
    client: GoogleGenAI,
    contents: string | Content,
    params: any,
    modelName: string = 'gemini-3-flash-preview'
): Promise<GenerateContentResponse> => {
    if (!navigator.onLine) {
        throw new Error("Estás sin conexión. Esta función requiere acceso a internet.");
    }
    
    // Ensure the config object exists and follow structural guidelines
    const { config, ...rest } = params;
    const finalConfig = { ...(config || {}) };

    // Auto-switch to valid thinking model if pro is requested
    if (modelName === 'gemini-3-pro-preview' && !finalConfig.thinkingConfig) {
         finalConfig.thinkingConfig = { thinkingBudget: 16000 };
    }

    // Move thinkingConfig inside the config object if it was passed at the top level
    if (params.thinkingConfig && !finalConfig.thinkingConfig) {
        finalConfig.thinkingConfig = params.thinkingConfig;
    }

    try {
        const result = await client.models.generateContent({
            model: modelName,
            contents,
            config: finalConfig,
            ...rest
        });
        return result;
    } catch (error: any) {
        console.error("Error en la solicitud a Gemini:", error);
        if (error.toString().includes('429') || (error.message && error.message.includes('RESOURCE_EXHAUSTED'))) {
             throw new Error("Límite de solicitudes a Gemini excedido. Por favor, revisa tu plan o inténtalo más tarde.");
        }
        throw error;
    }
};

/**
 * Executes a streaming generateContent request.
 */
const makeGenerateContentStreamRequest = async (
    client: GoogleGenAI,
    contents: Content,
    config: any,
    modelName: string = 'gemini-3-flash-preview'
): Promise<AsyncGenerator<GenerateContentResponse>> => {
    if (!navigator.onLine) {
        throw new Error("Estás sin conexión. Esta función requiere acceso a internet.");
    }
    try {
        const resultStream = await client.models.generateContentStream({
            model: modelName,
            contents,
            ...config,
        });
        return resultStream;
    } catch (error: any) {
        console.error("Error en la solicitud de streaming a Gemini:", error);
         if (error.toString().includes('429') || (error.message && error.message.includes('RESOURCE_EXHAUSTED'))) {
             throw new Error("Límite de solicitudes a Gemini excedido. Por favor, revisa tu plan o inténtalo más tarde.");
        }
        throw error;
    }
};

export function encode(bytes: Uint8Array) {
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

export function decode(base64: string) {
  const binaryString = atob(base64);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes;
}

export async function decodeAudioData(
  data: Uint8Array,
  ctx: AudioContext,
  sampleRate: number,
  numChannels: number,
): Promise<AudioBuffer> {
  const dataInt16 = new Int16Array(data.buffer);
  const frameCount = dataInt16.length / numChannels;
  const buffer = ctx.createBuffer(numChannels, frameCount, sampleRate);

  for (let channel = 0; channel < numChannels; channel++) {
    const channelData = buffer.getChannelData(channel);
    for (let i = 0; i < frameCount; i++) {
      channelData[i] = dataInt16[i * numChannels + channel] / 32768.0;
    }
  }
  return buffer;
}

const throwNotImplemented = (funcName: string) => {
    throw new Error(`La función '${funcName}' no está implementada para el proveedor Gemini (STUB).`);
};

// --- Concrete Implementations for Gemini ---

export const getAICoachAnalysis = async (exerciseName: string, _settings: Settings): Promise<{ summary: string; pros: string[]; cons: string[] }> => {
    const client = getClient();
    const prompt = `Proporciona un análisis de entrenador para "${exerciseName}". Incluye un resumen, 2-3 pros y 2-3 contras. Responde ÚNICAMENTE con JSON en español: { "summary": "string", "pros": ["string"], "cons": ["string"] }.`;
    const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json' } }, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', { summary: 'Análisis fallido.', pros: [], cons: [] });
};

export const getAICoachInsights = async (history: WorkoutLog[], programs: Program[], _settings: Settings, bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[]): Promise<CoachInsight> => {
    const client = getClient();
    const prompt = `Analiza las últimas 4 semanas de datos de entrenamiento. Identifica tendencias, posibles estancamientos o riesgos. Proporciona un hallazgo principal, 2-3 sugerencias accionables y un alertLevel ('info', 'warning', 'danger'). Responde ÚNICAMENTE con JSON en español. Datos: ${JSON.stringify({ history: history.slice(-20) })}`;
    const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json' }}, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', { title: 'Error', findings: 'No se pudo analizar.', suggestions: [], alertLevel: 'danger' });
};

export const generateCarpeDiemWeeklyPlan = async (program: Program, history: WorkoutLog[], _settings: Settings, calorieGoal: 'deficit' | 'maintenance' | 'surplus'): Promise<CarpeDiemPlan> => {
    const client = getClient();
    const systemInstruction = `Eres "Prime Coach", un experto mundial en periodización. Tu misión es actuar como planificador para un atleta en modo "Carpe Diem". Analizarás su programa, historial, y fatiga para generar un plan adaptativo para la semana actual. Responde SIEMPRE y ÚNICAMENTE con un objeto JSON en español.`;
    
    const recentHistory = history.slice(-10).map(log => ({
        sessionName: log.sessionName, date: log.date, fatigueLevel: log.fatigueLevel, sessionStressScore: log.sessionStressScore,
    }));

    const prompt = `Análisis Semanal para atleta. Programa: "${program.name}", Historial: ${JSON.stringify(recentHistory)}. Objetivo calórico: ${calorieGoal}. Genera 'coachMessage' (markdown) y 'modifiedSessions' (array de objetos Session). Mantén los mismos IDs de sesiones/ejercicios/series.`;
    
    const response = await makeGenerateContentRequest(client, prompt, { config: { systemInstruction, responseMimeType: 'application/json' } }, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', { coachMessage: '', modifiedSessions: [] });
};

export const generateBodyLabAnalysis = async (programs: Program[], history: WorkoutLog[], _settings: Settings): Promise<BodyLabAnalysis> => {
    const client = getClient();
    const systemInstruction = `Eres un analista de rendimiento deportivo. Analiza el perfil del atleta.`;
    const prompt = `Genera un análisis completo de perfil de atleta basado en sus datos. JSON: BodyLabAnalysis schema.`;
    // Fixed config to ensure thinkingConfig is properly nested
    const response = await makeGenerateContentRequest(client, prompt, { config: { systemInstruction, responseMimeType: 'application/json', thinkingConfig: { thinkingBudget: 16000 } } }, 'gemini-3-pro-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', { profileTitle: "Atleta", profileSummary: "Análisis no disponible", strongPoints: [], weakPoints: [], recoveryAnalysis: {score:5, summary:""}, frequencyAnalysis: {preferredType:"Mixta", summary:""}, recommendations: [] });
};

export const generateBiomechanicalAnalysis = async (data: BiomechanicalData, exercises: string[], _settings: Settings): Promise<BiomechanicalAnalysis> => {
     const client = getClient();
     const prompt = `Analiza la biomecánica con estos datos: ${JSON.stringify(data)}. JSON: BiomechanicalAnalysis schema.`;
     // Fixed config to ensure thinkingConfig is properly nested
     const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json', thinkingConfig: { thinkingBudget: 16000 } } }, 'gemini-3-pro-preview');
     // Guidelines: Access .text property directly
     return safeJsonParse(response.text || '', { apeIndex: {value: 1, interpretation: ""}, advantages: [], challenges: [], exerciseSpecificRecommendations: [] });
};

export const generateImage = async (prompt: string, aspectRatio: string, settings: Settings): Promise<string> => {
    const res = await generateImages(prompt, aspectRatio, settings);
    return res.imageUrls[0] || '';
};

export const generateImages = async (prompt: string, aspectRatio: string, _settings: Settings): Promise<{ imageUrls: string[] }> => {
    const client = getClient();
    const validRatios = ["1:1", "3:4", "4:3", "9:16", "16:9"];
    const safeRatio = validRatios.includes(aspectRatio) ? aspectRatio : "1:1";

    try {
        // Updated to use gemini-2.5-flash-image by default as per guidelines
        const response = await client.models.generateContent({
            model: 'gemini-2.5-flash-image',
            contents: { parts: [{ text: prompt }] },
            config: { imageConfig: { aspectRatio: safeRatio as any } },
        });
        
        const imageUrls: string[] = [];
        if (response.candidates?.[0]?.content?.parts) {
            for (const part of response.candidates[0].content.parts) {
                if (part.inlineData) {
                    imageUrls.push(`data:${part.inlineData.mimeType || 'image/jpeg'};base64,${part.inlineData.data}`);
                }
            }
        }
        if (imageUrls.length > 0) return { imageUrls };
        throw new Error("No data returned from Gemini.");

    } catch (flashError) {
        console.warn("Gemini Flash Image failed, falling back to Imagen...", flashError);
        // Fallback: Imagen models
        try {
             const response = await client.models.generateImages({
                model: 'imagen-3.0-generate-001',
                prompt: prompt,
                config: {
                    numberOfImages: 1,
                    aspectRatio: safeRatio as any,
                    outputMimeType: 'image/jpeg' 
                }
            });

            const imageUrls: string[] = [];
            if (response.generatedImages) {
                for (const img of response.generatedImages) {
                    if (img.image && img.image.imageBytes) {
                        imageUrls.push(`data:image/jpeg;base64,${img.image.imageBytes}`);
                    }
                }
            }
            if (imageUrls.length > 0) return { imageUrls };
            throw new Error("No images returned from Imagen");
        } catch (imagenError) {
             console.error("Imagen generation also failed:", imagenError);
             throw new Error("No se pudo generar la imagen. Por favor intenta más tarde.");
        }
    }
};

export const editImageWithText = async (base64Image: string, prompt: string, _settings: Settings): Promise<string> => {
    const client = getClient();
    try {
        const response = await client.models.generateContent({
            model: 'gemini-2.5-flash-image',
            contents: {
                parts: [
                    { inlineData: { mimeType: 'image/jpeg', data: base64Image } },
                    { text: prompt },
                ],
            },
        });

        if (response.candidates && response.candidates[0].content.parts) {
            for (const part of response.candidates[0].content.parts) {
                if (part.inlineData) {
                    return `data:${part.inlineData.mimeType || 'image/jpeg'};base64,${part.inlineData.data}`;
                }
            }
        }
        throw new Error("No edited image returned.");
    } catch (error) {
        console.error("Error editing image:", error);
        throw new Error("No se pudo editar la imagen.");
    }
};

// --- ALL OTHER FUNCTIONS ---

export const generateExerciseClassification = async (exerciseName: string, _settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => {
    const client = getClient();
    const prompt = `Clasifica el ejercicio: "${exerciseName}". Responde JSON: {type, category, equipment, force}.`;
    const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json' } }, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', {});
};

export const analyzeExerciseMuscles = async (exerciseName: string, _settings: Settings): Promise<{ involvedMuscles: ExerciseMuscleInfo['involvedMuscles'] }> => {
    const client = getClient();
    const muscleListString = MUSCLE_GROUPS.filter(g => g !== 'Todos').join('", "');
    const prompt = `Analiza músculos para "${exerciseName}". JSON: { involvedMuscles: [{ muscle: (uno de "${muscleListString}"), activation: 0.1-1.0, role: "primary"|"secondary"|"stabilizer" }] }.`;
    const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json' } }, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', { involvedMuscles: [] });
};

export const getCoachChatResponseStream = async function* (prompt: string, messages: ChatMessage[], programs: Program[], history: WorkoutLog[], _settings: Settings, sessionContext?: Session | OngoingWorkoutState) {
    const client = getClient();
    const systemInstruction = `Eres "Prime Coach", un entrenador de fitness servicial, experto y alentador. Responde en español y en formato Markdown. Contexto: ${JSON.stringify({programs: (programs || []).map(p=>p.name), history: (history || []).slice(-5).map(l=>l.sessionName)})}`;
    const allMessages: Content[] = [...(messages || []).slice(-10), { role: 'user', parts: [{ text: prompt }] }];
    const resultStream = await makeGenerateContentStreamRequest(client, { parts: allMessages as any }, { config: { systemInstruction }}, 'gemini-3-flash-preview');
    for await (const chunk of resultStream) {
        // Guidelines: Access .text property directly
        yield { text: chunk.text || '' };
    }
};

export const getPhysicalProgressChatResponseStream = async function*(prompt: string, messages: ChatMessage[], bodyProgress: BodyProgressLog[], nutritionLogs: NutritionLog[], _settings: Settings) {
    const client = getClient();
    const systemInstruction = `Eres un coach de nutrición. Responde en español. Contexto: ${JSON.stringify({bodyProgress: (bodyProgress || []).slice(-5), nutrition: (nutritionLogs || []).slice(-5)})}`;
    const allMessages: Content[] = [...(messages || []).slice(-10), { role: 'user', parts: [{ text: prompt }] }];
    const resultStream = await makeGenerateContentStreamRequest(client, { parts: allMessages as any }, { config: { systemInstruction }}, 'gemini-3-flash-preview');
    for await (const chunk of resultStream) {
        // Guidelines: Access .text property directly
        yield { text: chunk.text || '' };
    }
};

export const generateSpeech = async (text: string, settings: Settings): Promise<string | null> => {
    const client = getClient();
    const response = await client.models.generateContent({
        model: "gemini-2.5-flash-preview-tts",
        contents: [{ parts: [{ text: `Say cheerfully: ${text}` }] }],
        config: {
            responseModalities: [Modality.AUDIO],
            // Fixed speechConfig nesting to match guidelines
            speechConfig: { 
                voiceConfig: { 
                    prebuiltVoiceConfig: { voiceName: settings.aiVoice || 'Puck' } 
                } 
            },
        },
    });
    return response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data || null;
}

export const createAndPopulateExercise = async (exerciseName: string, _settings: Settings): Promise<Partial<ExerciseMuscleInfo>> => {
    const client = getClient();
    const prompt = `Genera un análisis kinesiológico completo para el ejercicio: "${exerciseName}". Responde ÚNICAMENTE con JSON.`;
    // Fixed config to ensure thinkingConfig is properly nested
    const response = await makeGenerateContentRequest(client, prompt, { config: { responseMimeType: 'application/json', thinkingConfig: { thinkingBudget: 16000 } } }, 'gemini-3-pro-preview');
    // Guidelines: Access .text property directly
    return safeJsonParse(response.text || '', {});
};

export const generateFoodCategoryDescription = async (categoryName: string, _settings: Settings): Promise<string> => {
    const client = getClient();
    const prompt = `Escribe una descripción breve y motivadora (2-3 frases) sobre la importancia de la categoría de alimentos "${categoryName}" para un atleta.`;
    const response = await makeGenerateContentRequest(client, prompt, {}, 'gemini-3-flash-preview');
    // Guidelines: Access .text property directly
    return response.text || '';
}

// Add stubs for the rest of the functions to satisfy the AiFunction type.
export const generateWeeklyProgressAnalysis = async (...args: any): Promise<any> => throwNotImplemented('generateWeeklyProgressAnalysis');
export const generateCorrelationAnalysis = async (...args: any): Promise<any> => throwNotImplemented('generateCorrelationAnalysis');
export const generateExerciseProgressReport = async (...args: any): Promise<any> => throwNotImplemented('generateExerciseProgressReport');
export const generateWeightProjection = async (...args: any): Promise<any> => throwNotImplemented('generateWeightProjection');
export const generateExercisesForPurpose = async (...args: any): Promise<any> => throwNotImplemented('generateExercisesForPurpose');
export const analyzeProgressPhoto = async (...args: any): Promise<any> => throwNotImplemented('analyzeProgressPhoto');
export const getNutritionalInfo = async (...args: any): Promise<any> => throwNotImplemented('getNutritionalInfo');
export const analyzeMealPhoto = async (...args: any): Promise<any> => throwNotImplemented('analyzeMealPhoto');
export const searchGoogleImages = async (...args: any): Promise<any> => throwNotImplemented('searchGoogleImages');
export const estimateSFR = async (...args: any): Promise<any> => throwNotImplemented('estimateSFR');
export const createAndPopulateFoodItem = async (...args: any): Promise<any> => throwNotImplemented('createAndPopulateFoodItem');
export const generateKinesiologyAnalysis = async (...args: any): Promise<any> => throwNotImplemented('generateKinesiologyAnalysis');
export const getPeriodizationCoachStream = async function* (...args: any): AsyncGenerator<GenerateContentResponse> { yield { text: 'Not implemented' } as any; };
export const batchGenerateExercises = async (...args: any): Promise<any> => throwNotImplemented('batchGenerateExercises');
export const generatePerformanceAnalysis = async (...args: any): Promise<any> => throwNotImplemented('generatePerformanceAnalysis');
export const generateWeekFromPrompt = async (...args: any): Promise<any> => throwNotImplemented('generateWeekFromPrompt');
export const analyzeNutritionPlanDocument = async (...args: any): Promise<any> => throwNotImplemented('analyzeNutritionPlanDocument');
export const generateTasksFromWorkout = async (...args: any): Promise<any> => throwNotImplemented('generateTasksFromWorkout');
export const getNutritionalInfoForPantryItem = async (...args: any): Promise<any> => throwNotImplemented('getNutritionalInfoForPantryItem');
export const generateMealSuggestion = async (...args: any): Promise<any> => throwNotImplemented('generateMealSuggestion');
export const generateMealsFromPantry = async (...args: any): Promise<any> => throwNotImplemented('generateMealsFromPantry');
export const generateMobilityRoutine = async (...args: any): Promise<any> => throwNotImplemented('generateMobilityRoutine');
export const generateMuscleGroupAnalysis = async (...args: any): Promise<any> => throwNotImplemented('generateMuscleGroupAnalysis');
export const generateWorkoutPostSummary = async (...args: any): Promise<any> => throwNotImplemented('generateWorkoutPostSummary');
export const generateOnThisDayMessage = async (...args: any): Promise<any> => throwNotImplemented('generateOnThisDayMessage');
export const suggestExerciseAlternatives = async (...args: any): Promise<any> => throwNotImplemented('suggestExerciseAlternatives');
export const generateExerciseAlias = async (...args: any): Promise<any> => throwNotImplemented('generateExerciseAlias');
export const analyzeWorkoutVolume = async (...args: any): Promise<any> => throwNotImplemented('analyzeWorkoutVolume');
export const getPrimeStarsRating = async (...args: any): Promise<any> => throwNotImplemented('getPrimeStarsRating');
export const getCommunityOpinionForExercise = async (...args: any): Promise<any> => throwNotImplemented('getCommunityOpinionForExercise');
export const analyzePosturePhoto = async (...args: any): Promise<any> => throwNotImplemented('analyzePosturePhoto');
export const analyzeExerciseVideo = async (...args: any): Promise<any> => throwNotImplemented('analyzeExerciseVideo');
export const searchWebForExerciseImages = async (...args: any): Promise<any> => throwNotImplemented('searchWebForExerciseImages');
export const searchWebForExerciseVideos = async (...args: any): Promise<any> => throwNotImplemented('searchWebForExerciseVideos');
export const getCommunityHighlights = async (...args: any): Promise<any> => throwNotImplemented('getCommunityHighlights');
export const generateSessionScore = async (...args: any): Promise<any> => throwNotImplemented('generateSessionScore');
export const generateImprovementSuggestions = async (...args: any): Promise<any> => throwNotImplemented('generateImprovementSuggestions');
export const generateTimeSaverSuggestions = async (...args: any): Promise<any> => throwNotImplemented('generateTimeSaverSuggestions');
export const getAIGlobalRating = async (...args: any): Promise<any> => throwNotImplemented('getAIGlobalRating');
export const analyzeWeeklyProgress = async (...args: any): Promise<any> => throwNotImplemented('analyzeWeeklyProgress');
export const generateExerciseDescription = async (...args: any): Promise<any> => throwNotImplemented('generateExerciseDescription');
